(ns parcera.antlr.javascript
  ;; TODO: does this even works ?
  ;; TODO: translate the index.js file to Clojurescript 😥
  ;; TODO: how do I get a Clojurescript repl ... I am blind without it
  ;; am I suppose to code the whole thing and hope that it works by running
  ;; the tests 🤔 ... I can feel the pain of other languages 😭
  (:require [parcera.antlr.protocols :as antlr]
            [antlr4.index :as runtime :refer [ParserRuleContext]]
            [parcera.antlr.clojureLexer :as clojureLexer]
            [parcera.antlr.clojureParser :as clojureParser]))


(set! *warn-on-infer* true)


(extend-type ParserRuleContext
  antlr/ParserRule
  (children [^ParserRuleContext this] (.-children this))
  (rule-index [^ParserRuleContext this] (.getRuleIndex this))
  (start [^ParserRuleContext this] (.getStart this))
  (end [^ParserRuleContext this] (.getStop this)))


(extend-type ErrorNodeImpl
  antlr/ErrorNode
  (token [^ErrorNodeImpl this] (.-symbol this)))


(extend-type Token
  antlr/Token
  (row [^Token this] (.getLine this))
  (column [^Token this] (.getCharPositionInLine this)))


(extend-type clojureParser
  antlr/AntlrParser
  (rules [^clojureParser this] (vec (.getRuleNames this)))
  (tree [^clojureParser this] (. this (code))))


(defn parser
  [input listener]
  (let [chars  (CharStreams/fromString input)
        lexer  (doto (new clojureLexer chars)
                 (.removeErrorListeners))
        ;; todo: how to handle lexer errors ?
        ;(.addErrorListener listener))
        tokens (new CommonTokenStream lexer)]
    (doto (new clojureParser tokens)
      (.setBuildParseTree true)
      (.removeErrorListeners)
      (.addErrorListener listener))))
