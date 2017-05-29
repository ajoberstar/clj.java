(ns ike.cljj.file
  "A straightforward wrapper of common java.nio.file.* functionality. All functions
  use the Pathish protocol to turn their arguments into Paths."
  (:refer-clojure :exclude [list])
  (:require [ike.cljj.stream :as stream]
            [ike.cljj.function :refer [defsam]])
  (:import (java.nio.file Path Paths Files CopyOption LinkOption OpenOption FileVisitOption SimpleFileVisitor FileVisitResult)
           (java.nio.file.attribute FileAttribute)
           (java.nio.charset StandardCharsets)
           (java.io File)
           (java.net URI)))

(defprotocol Pathish
  "Implement this protocl if your type can be converted to a
  java.nio.file.Path object."
  (as-path [x]))

(extend-protocol Pathish
  nil
  (as-path [x] nil)
  String
  (as-path [x] (Paths/get x (into-array String [])))
  Path
  (as-path [x] x)
  File
  (as-path [x] (.toPath x))
  URI
  (as-path [x] (Paths/get x)))

(defn path
  "Creates a Path using the segments provided."
  [x & more]
  (let [more-array (into-array String more)]
    (Paths/get x more-array)))

(defn exists?
  "Tests whether the path exists."
  [path]
  (Files/exists (as-path path) (into-array LinkOption [])))

(defn file?
  "Tests whether the path is a file."
  [path]
  (Files/isRegularFile (as-path path) (into-array LinkOption [])))

(defn dir?
  "Tests whether the path is a directory."
  [path]
  (Files/isDirectory (as-path path) (into-array LinkOption [])))

(defn link?
  "Tests whether the path is a symbolic link."
  [path]
  (Files/isSymbolicLink (as-path path)))

(defn same?
  "Tests whether two paths point to the same file."
  [x y]
  (Files/isSameFile (as-path x) (as-path y)))

(defn size
  "Calculates the size of the file at the path."
  [path]
  (Files/size (as-path path)))

(defn list
  "Lists the immediate children of a directory."
  [path]
  (Files/list (as-path path)))

(defn walk
  "Walks the file tree (depth-first) below a directory, returning a Stream. The first element
  will always be the given path."
  ([path] (Files/walk (as-path path) (into-array FileVisitOption [])))
  ([path max-depth] (Files/walk (as-path path) max-depth (into-array FileVisitOption []))))

(defn make-dir
  "Creates an empty directory at the path. Optionally can specify to create parent directories, as well."
  ([path] (make-dir path false))
  ([path parents?]
   (let [path (as-path path)]
     (if parents?
       (Files/createDirectories path (into-array FileAttribute []))
       (Files/createDirectory path (into-array FileAttribute []))))))

(defn make-file
  "Creates an empty file at the path."
  [path]
  (Files/createFile (as-path path) (into-array FileAttribute [])))

(defn make-link
  "Creates a symbolic link from 'path' to 'target'."
  [path target]
  (Files/createSymbolicLink (as-path path) (as-path target) (into-array FileAttribute [])))

(defn temp-dir
  "Creates a temporary dir. Uses java.io.tmpdir as the parent folder unless 'dir' is provided."
  ([prefix]
   (Files/createTempDirectory prefix (into-array FileAttribute [])))
  ([dir prefix]
   (Files/createTempDirectory (as-path dir) prefix (into-array FileAttribute []))))

(defn temp-file
  "Creates a temporary file. Uses java.io.tmpdir as the parent folder unless 'dir' is provided."
  ([prefix suffix]
   (Files/createTempFile prefix suffix (into-array FileAttribute [])))
  ([dir prefix suffix]
   (Files/createTempFile (as-path dir) prefix suffix (into-array FileAttribute []))))

(declare delete)

(def ^:private delete-visitor
  (proxy [SimpleFileVisitor] []
    (visitFile [file _]
      (delete file)
      FileVisitResult/CONTINUE)
    (postVisitDirectory [dir _]
      (delete dir)
      FileVisitResult/CONTINUE)))

(defn delete
  "Deletes a file or directory. Will not fail if the path does not exist. Can optionally
  delete recursively."
  ([path] (delete path false))
  ([path recurse]
   (let [path (as-path path)]
    (if (and recurse (dir? path))
      (Files/walkFileTree path delete-visitor)
      (Files/deleteIfExists path)))))

(defn move
  "Moves the file or directory from 'path' to 'target'."
  [path target]
  (Files/move (as-path path) (as-path target) (into-array CopyOption [])))

(defn copy
  "Copies a file or directory. Can optionally copy the directory recursively."
  ([from to] (copy from to false))
  ([from to recurse]
   (if (and recurse (dir? from))
    (with-open [stream (-> from walk)]
      (doseq [ffile (rest (stream/stream-seq stream))]
        (let [rpath (.relativize from ffile)
              tfile (.resolve to rpath)]
          (copy ffile tfile))))
    (Files/copy (as-path from) (as-path to) (into-array CopyOption [])))))

(defn read-bytes
  "Reads all bytes from a file and returns the byte[]."
  [path]
  (Files/readAllBytes (as-path path)))

(defn read-lines
  "Reads all lines from a file and returns them in a Stream."
  [path]
  (Files/lines (as-path path) (StandardCharsets/UTF_8)))

(defn read-str
  "Reads all bytes from a file and returns as a String (using UTF-8)."
  [path]
  (-> path read-lines (.getBytes (StandardCharsets/UTF_8))))

(defn write-bytes
  "Writes all bytes to a file (truncating any existing content)."
  [path bytes]
  (Files/write (as-path path) bytes (into-array OpenOption [])))

(defn write-lines
  "Writes all lines to a file (truncating any existing content)."
  [path lines]
  (Files/write (as-path path) lines (StandardCharsets/UTF_8) (into-array OpenOption [])))
