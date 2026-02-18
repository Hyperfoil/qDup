///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 17+
//DEPS io.hyperfoil.tools:qDup-lsp:0.11.1-SNAPSHOT

import io.hyperfoil.tools.qdup.lsp.QDupLspLauncher;

class qduplsp {

    public static void main(String... args) {
        QDupLspLauncher.main(args);
    }

}
