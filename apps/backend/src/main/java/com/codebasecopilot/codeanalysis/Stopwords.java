package com.codebasecopilot.codeanalysis;

import java.util.Set;

// Part of IdentifierTokenizer.VERSION: changing this list creates a new lexical version.
final class Stopwords {

    private static final String PT_BR =
            "a o as os um uma uns umas de do da dos das dum duma em no na nos nas "
                    + "num numa ao aos à às por pelo pela pelos pelas para pra com sem sob "
                    + "sobre entre até ate e ou mas se que qual quais quando onde como porque "
                    + "porquê quem quanto quanta quantos quantas é são sao está esta estão "
                    + "estao ser foi há tem eu me você voce vocês voces ele ela eles elas "
                    + "isso isto esse essa esses essas este estes estas aquele aquela seu sua "
                    + "seus suas meu minha não nao já ja também tambem mais ";

    private static final String EN =
            "an the of in on at to for from by with without into about and or but "
                    + "not is are was were be been being does did has have had can could "
                    + "should would will it its this that these those there here what which "
                    + "who whom whose when where why how i my we our you your he she they "
                    + "them their his her if so than then ";

    private static final Set<String> WORDS = Set.of((PT_BR + EN).trim().split(" "));

    private Stopwords() {}

    static boolean contains(String lowercaseWord) {
        return WORDS.contains(lowercaseWord);
    }
}
