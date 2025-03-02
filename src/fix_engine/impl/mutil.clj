(ns fix-engine.impl.mutil
  (:import
   [java.util.concurrent.locks ReentrantLock]
  )
  )


(defn rlock []
  (ReentrantLock.))

(defmacro with-lock
  "Executes exprs in an implicit do, while holding the lock of l.
Will release the lock of l in all circumstances."
  {:added "1.0"}
  [l & body]
  `(let [lockee# ~l]
     (try
       (let [locklocal# lockee#]
         (.lock locklocal#)
         (try
           ~@body
           (finally
             (.unlock locklocal#)))))))