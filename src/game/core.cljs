(ns game.core
  (:require-macros
    [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require
    [weasel.repl :as repl]
    [cljs.reader :refer [read-string]]
    [game.board :refer [piece-fits?
                        rotate-piece
                        start-position
                        empty-board
                        get-drop-pos
                        get-rand-piece
                        get-rand-diff-piece
                        write-piece-to-board
                        write-piece-behind-board
                        create-drawable-board
                        get-filled-row-indices
                        clear-rows
                        game-over-row
                        collapse-rows
                        highlight-rows
                        write-to-board
                        n-rows
                        n-cols
                        rows-cutoff
                        next-piece-board
                        tower-height]]
    [game.rules :refer [get-points
                        level-up?
                        get-level-speed]]
    [game.paint :refer [size-canvas!
                        cell-size
                        draw-board!]]
    [cljs.core.async :refer [close! put! chan <! timeout unique alts!]]))

(enable-console-print!)

(def $ js/jQuery)

;;------------------------------------------------------------
;; STATE OF THE GAME
;;------------------------------------------------------------

(def state
  "The state of the game."
  (atom nil))

(def key-states
  "The state of the directional keys."
  (atom nil))

(defn init-state!
  "Set the initial state of the game."
  []
  (reset! key-states {:left false
                      :right false
                      :down false})
  (reset! state {:next-piece nil
                 :piece nil
                 :position nil
                 :board empty-board

                 :score 0
                 :level 0
                 :level-lines 0
                 :total-lines 0

                 :soft-drop false
                 }))

; required for pausing/resuming the gravity routine
(def pause-grav (chan))
(def resume-grav (chan))

;;------------------------------------------------------------
;; STATE MONITOR
;;------------------------------------------------------------

(defn make-redraw-chan
  "Create a channel that receives a value everytime a redraw is requested."
  []
  (let [redraw-chan (chan)
        request-anim #(.requestAnimationFrame js/window %)]
    (letfn [(trigger-redraw []
              (put! redraw-chan 1)
              (request-anim trigger-redraw))]
      (request-anim trigger-redraw)
      redraw-chan)))

(defn drawable-board
  "Draw the current state of the board."
  []
  (let [{piece :piece
         [x y] :position
         board :board} @state]
    (create-drawable-board piece x y board)))

(defn go-go-draw!
  "Kicks off the drawing routine."
  []
  (let [redraw-chan (make-redraw-chan)]
    (go-loop [board nil]
      (<! redraw-chan)
      (let [new-board (drawable-board)
            next-piece (:next-piece @state)]
        (when (not= board new-board)
          (draw-board! "game-canvas" new-board cell-size rows-cutoff)
          (draw-board! "next-canvas" (next-piece-board next-piece) cell-size))
        (recur new-board)))))

;;------------------------------------------------------------
;; Game-driven STATE CHANGES
;;------------------------------------------------------------

(defn go-go-game-over!
  "Kicks off game over routine. (and get to the chopper)"
  []
  (go
    (doseq [y (reverse (range n-rows))]
      (<! (timeout 10))
      (swap! state assoc-in [:board y] (game-over-row)))))

(defn spawn-piece!
  "Spawns the given piece at the starting position."
  [piece]
    (swap! state assoc :piece piece
                       :position start-position)

    (put! resume-grav 0))

(defn try-spawn-piece!
  "Checks if new piece can be written to starting position."
  []
  (let [piece (or (:next-piece @state) (get-rand-piece))
        next-piece (get-rand-diff-piece piece)
        [x y] start-position
        board (:board @state)]

    (swap! state assoc :next-piece next-piece)

    (if (piece-fits? piece x y board)
      (spawn-piece! piece)
      (go ;exitable
        ; Show piece that we attempted to spawn, drawn behind the other pieces.
        ; Then pause before kicking off gameover animation.
        (swap! state update-in [:board] #(write-piece-behind-board piece x y %))
        (<! (timeout (get-level-speed (:level @state))))
        (go-go-game-over!)))))

(defn display-points!
  []
  (.html ($ "#score") (str "Score: " (:score @state)))
  (.html ($ "#level") (str "Level: " (:level @state)))
  (.html ($ "#lines") (str "Lines: " (:total-lines @state)))
  )

(defn update-points!
  [rows-cleared]
  (let [n rows-cleared
        level (:level @state)
        points (get-points n (inc level))
        level-lines (+ n (:level-lines @state))]

    ; update the score before a possible level-up
    (swap! state update-in [:score] + points)

    (if (level-up? level-lines)
      (do
        (swap! state update-in [:level] inc)
        (swap! state assoc :level-lines 0))
      (swap! state assoc :level-lines level-lines))

    (swap! state update-in [:total-lines] + n)
    )

  (display-points!))

(defn collapse-rows!
  "Collapse the given row indices."
  [rows]
  (let [n (count rows)
        board (collapse-rows rows (:board @state))]
    (swap! state assoc :board board)
    (update-points! n)))

(defn go-go-collapse!
  "Starts the collapse animation if we need to, returning nil or the animation channel."
  []
  (let [board (:board @state)
        rows (get-filled-row-indices board)
        flashed-board (highlight-rows rows board)
        cleared-board (clear-rows rows board)]

    (when-not (zero? (count rows))
      (go ; no need to exit this (just let it finish)
        ; blink n times
        (doseq [i (range 3)]
          (swap! state assoc :board flashed-board)
          (<! (timeout 170))
          (swap! state assoc :board board)
          (<! (timeout 170)))

        ; clear rows to create a gap, and pause
        (swap! state assoc :board cleared-board)
        (<! (timeout 220))

        ; finally collapse
        (collapse-rows! rows)))))

(defn lock-piece!
  "Lock the current piece into the board."
  []
  (let [[x y] (:position @state)
        piece (:piece @state)
        board (:board @state)
        new-board (write-piece-to-board piece x y board)]
    (swap! state assoc :board new-board
                       :piece nil
                       :soft-drop false) ; reset soft drop
    (put! pause-grav 0)

    ; If collapse routine returns a channel...
    ; then wait for it before spawning a new piece.
    (if-let [collapse-anim (go-go-collapse!)]
      (go
        (<! collapse-anim)
        (<! (timeout 100))
        (try-spawn-piece!))
      (try-spawn-piece!))))

(defn apply-gravity!
  "Move current piece down 1 if possible, else lock the piece."
  []
  (let [piece (:piece @state)
        [x y] (:position @state)
        board (:board @state)
        ny (inc y)]
    (if (piece-fits? piece x ny board)
      (swap! state assoc-in [:position 1] ny)
      (lock-piece!))))

(defn go-go-gravity!
  "Starts the gravity routine."
  []
  ; Make sure gravity starts in paused mode.
  ; Spawning the piece will signal the first "resume".
  (put! pause-grav 0)

  (go-loop []
    (let [soft-speed 35
          level-speed (get-level-speed (:level @state))

          ; only soft-drop if we're currently not shifting
          shifting (or (:left @key-states) (:right @key-states))
          soft-drop (and (not shifting) (:soft-drop @state))

          speed (if soft-drop
                  (min soft-speed level-speed)
                  level-speed)
          time-chan (timeout speed)
          [_ c] (alts! [time-chan pause-grav])]

      (condp = c

        pause-grav
        (do (<! resume-grav)
            (recur))

        time-chan
        (do
          (apply-gravity!)
          (recur))

        nil))))


;;------------------------------------------------------------
;; Input-driven STATE CHANGES
;;------------------------------------------------------------

(defn try-move!
  "Try moving the current piece to the given offset."
  [dx dy]
  (let [[x y] (:position @state)
        piece (:piece @state)
        board (:board @state)
        nx (+ dx x)
        ny (+ dy y)]
    (if (piece-fits? piece nx ny board)
      (swap! state assoc :position [nx ny]))))

(defn try-rotate!
  "Try rotating the current piece."
  []
  (let [[x y] (:position @state)
        piece (:piece @state)
        board (:board @state)
        new-piece (rotate-piece piece)]
    (if (piece-fits? new-piece x y board)
      (swap! state assoc :piece new-piece))))

(defn hard-drop!
  "Hard drop the current piece."
  []
  (let [[x y] (:position @state)
        piece (:piece @state)
        board (:board @state)
        ny (get-drop-pos piece x y board)]
    (swap! state assoc :position [x ny])
    (lock-piece!)))

(def key-names {
  37 :left
  38 :up
  39 :right
  40 :down
  32 :space
  16 :shift})

(defn add-key-events
  "Add all the key inputs."
  []
  (let [down-chan (chan)
        key-name #(-> % .-keyCode key-names)
        key-down (fn [e]
                   (case (key-name e)
                     nil)
                   (when (:piece @state)
                     (case (key-name e)
                       :down  (put! down-chan true)
                       :left  (do (try-move! -1  0) (swap! key-states assoc :left true))
                       :right (do (try-move!  1  0) (swap! key-states assoc :right true))
                       :space (hard-drop!)
                       :up    (try-rotate!)
                       nil))
                   (when (#{:down :left :right :space :up} (key-name e))
                     (.preventDefault e)))
        key-up (fn [e]
                 (case (key-name e)
                   :left (swap! key-states assoc :left false)
                   :right (swap! key-states assoc :right false)
                   :down  (put! down-chan false)
                   nil)
                 (when (#{:left :right} (key-name e))
                   ; force gravity to reset
                   (put! pause-grav 0)
                   (put! resume-grav 0))
                 )]

    ; Add key events
    (.addEventListener js/window "keydown" key-down)
    (.addEventListener js/window "keyup" key-up)

    ; Prevent the player from holding the down-key
    ; for more than one piece-drop.
    ;
    ; The soft-drop state is set to:
    ;   = the state of the down-key when it CHANGES
    ;   = off, after a piece is locked
    (let [uc (unique down-chan)]
      (go-loop []
        (let [value (<! uc)]
          (swap! state assoc :soft-drop value))

        ; force gravity to reset
        (put! pause-grav 0)
        (put! resume-grav 0)
        (recur)))

    ))

;;------------------------------------------------------------
;; Entry Point
;;------------------------------------------------------------

(defn init []

  (repl/connect "ws://localhost:9001")

  (init-state!)

  (size-canvas! "game-canvas" empty-board cell-size rows-cutoff)
  (size-canvas! "next-canvas" (next-piece-board) cell-size)

  (try-spawn-piece!)
  (add-key-events)
  (go-go-draw!)
  (go-go-gravity!)

  (display-points!)
  )

($ init)
