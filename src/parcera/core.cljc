(ns parcera.core
  (:require [instaparse.core :as instaparse]
            [instaparse.combinators-source :as combi]
            [instaparse.cfg :as cfg]
            [parcera.terminals :as terminal])
  #?(:cljs (:import goog.string.StringBuffer)))

(def grammar-rules
  "code: form*;

    <form>: whitespace ( literal
                        | symbol
                        | collection
                        | reader-macro
                        )
            whitespace;

    whitespace = #'[,\\s]*'

    <collection>: &#'[\\(\\[{#]'  ( list
                                  | vector
                                  | map
                                  | set
                                  )
                                  ;

    list: <'('> form* <')'> ;

    vector: <'['> form* <']'> ;

    map: map-namespace? <'{'> map-content <'}'> ;

    map-namespace: <'#'> ( keyword | auto-resolve );

    map-content: (form form)*

    auto-resolve: '::';

    set: <'#{'> form* <'}'> ;

    <literal>:
          number
        | string
        | character
        | keyword
        | comment
        | symbolic
        ;

    symbolic: #'##(Inf|-Inf|NaN)'

    <reader-macro>:
          dispatch
        | metadata
        | deref
        | quote
        | backtick
        | unquote
        | unquote-splicing
        ;

    <dispatch>: &'#' ( function | regex | var-quote | discard | tag | conditional | conditional-splicing);

    function: <'#'> list;

    metadata: <'^'> ( map | shorthand-metadata ) form;

    <shorthand-metadata>: ( symbol | string | keyword );

    regex: <'#'> string;

    var-quote: <'#\\''> symbol;

    quote: <'\\''> form;

    backtick: <'`'> form;

    unquote: <#'~(?!@)'> form;

    unquote-splicing: <'~@'> form;

    deref: <'@'> form;

    discard: <'#_'> form;

    tag: <#'#(?![_?])'> symbol form;

    conditional: <'#?'> list;

    conditional-splicing: <'#?@'> list;

    string : #'\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"';

    symbol: !number symbol-body

    <keyword>: simple-keyword | macro-keyword ;

    comment: #';.*';")

(def grammar-terminals
  {:character      (combi/regexp terminal/character-pattern)
   :symbol-body    (combi/hide-tag (combi/regexp terminal/symbol-pattern))
   :number         (combi/regexp terminal/number-pattern)
   :macro-keyword  (combi/regexp terminal/macro-keyword)
   :simple-keyword (combi/regexp terminal/simple-keyword)})

(def grammar (merge (cfg/ebnf grammar-rules) grammar-terminals))


(def clojure
  "Clojure (instaparse) parser. It can be used as:
  - (parcera/clojure input-string)
     -> returns an AST representation of input-string
  - (instaparse/parse parcera/clojure input-string)
     -> same as above but more explicit
  - (instaparse/parses parcera/clojure input-string)
   -> returns a sequence of possible AST representations in case of ambiguity
      in input-string

   For a description of all possible options, visit Instaparse's official
   documentation: https://github.com/Engelberg/instaparse#reference"
  (instaparse/parser grammar :start :code))


(defn- code*
  "internal function used to imperatively build up the code from the provided
   AST as Clojure's str would be too slow"
  [ast #?(:clj ^StringBuilder string-builder
          :cljs ^StringBuffer string-builder)]
  (case (first ast)
    :code
    (doseq [child (rest ast)]
      (code* child string-builder))

    :list
    (do (. string-builder (append "("))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append ")")))

    :vector
    (do (. string-builder (append "["))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append "]")))

    :map
    (doseq [child (rest ast)] (code* child string-builder))

    :map-namespace
    (do (. string-builder (append "#"))
        (code* (second ast) string-builder))

    :map-content
    (do (. string-builder (append "{"))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append "}")))

    :set
    (do (. string-builder (append "#{"))
        (doseq [child (rest ast)] (code* child string-builder))
        (. string-builder (append "}")))

    (:number :whitespace :symbolic :auto-resolve :symbol :simple-keyword
     :macro-keyword :comment :character :string)
    (. string-builder (append (second ast)))

    :metadata
    (do (. string-builder (append "^"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :quote
    (do (. string-builder (append "'"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :regex
    (do (. string-builder (append "#"))
        (code* (second ast) string-builder))

    :var-quote
    (do (. string-builder (append "#'"))
        (code* (second ast) string-builder))

    :discard
    (do (. string-builder (append "#_"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :tag
    (do (. string-builder (append "#"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :backtick
    (do (. string-builder (append "`"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :unquote
    (do (. string-builder (append "~"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :unquote-splicing
    (do (. string-builder (append "~@"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :conditional
    (do (. string-builder (append "#?"))
        (code* (second ast) string-builder))

    :conditional-splicing
    (do (. string-builder (append "#?@"))
        (code* (second ast) string-builder))

    :deref
    (do (. string-builder (append "@"))
        (doseq [child (rest ast)] (code* child string-builder)))

    :function
    (do (. string-builder (append "#"))
        (code* (second ast) string-builder))))


(defn code
  "Transforms your AST back into code

   ast: The nested sequence of [:keyword & content] which MUST follow the
        same structure as the result of `(parcera/clojure input-string)`

   Returns a string representation of the provided AST

   In general (= input (parcera/code (parcera/clojure input)))"
  [ast]
  (let [string-builder #?(:clj (new StringBuilder)
                          :cljs (new StringBuffer))]
    (code* ast string-builder)
    (. string-builder (toString))))

; Successful parse.
; Profile:  {:create-node 1651, :push-full-listener 2, :push-stack 1651, :push-listener 1689, :push-result 273, :push-message 275}
; "Elapsed time: 141.452323 msecs"
#_(time (clojure (str '(ns parcera.core
                         (:require [instaparse.core :as instaparse]
                                   [clojure.data :as data]
                                   [clojure.string :as str])))
                 :trace true))