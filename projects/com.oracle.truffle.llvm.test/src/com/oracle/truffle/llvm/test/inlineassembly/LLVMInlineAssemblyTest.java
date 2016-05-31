/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.test.inlineassembly;

import java.io.File;
import java.io.IOException;

import org.junit.Assert;
import org.junit.Test;

import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.test.LLVMPaths;
import com.oracle.truffle.llvm.tools.Clang;
import com.oracle.truffle.llvm.tools.Clang.ClangOptions;
import com.oracle.truffle.llvm.tools.Opt;
import com.oracle.truffle.llvm.tools.Opt.OptOptions;
import com.oracle.truffle.llvm.tools.Opt.OptOptions.Pass;

@SuppressWarnings({"static-method"})
public final class LLVMInlineAssemblyTest {

    private static final String PATH = LLVMPaths.LOCAL_TESTS + "/../inlineassemblytests";

    @Test
    public void test001() {
        String file = "inlineassembly001";
        Assert.assertEquals(42, run(file));
    }

    @Test
    public void test002() {
        String file = "inlineassembly002";
        Assert.assertEquals(42, run(file));
    }

    @Test
    public void test003() {
        String file = "inlineassembly003";
        Assert.assertEquals(-1, run(file));
    }

    @Test
    public void test004() {
        String file = "inlineassembly004";
        Assert.assertEquals(1, run(file));
    }

    @Test
    public void test005() {
        String file = "inlineassembly005";
        Assert.assertEquals(13, run(file));
    }

    @Test
    public void test006() {
        String file = "inlineassembly006";
        Assert.assertEquals(12, run(file));
    }

    @Test
    public void test007() {
        String file = "inlineassembly007";
        Assert.assertEquals(189, run(file));
    }

    @Test
    public void test008() {
        String file = "inlineassembly008";
        Assert.assertEquals(43, run(file));
    }

    @Test
    public void test009() {
        String file = "inlineassembly009";
        Assert.assertEquals(55, run(file));
    }

    @Test
    public void test010() {
        String file = "inlineassembly010";
        Assert.assertEquals(61, run(file));
    }

// @Test
// public void test011() {
// String file = "inlineassembly011";
// Assert.assertEquals(55, run(file));
// }

    @Test
    public void test012() {
        String file = "inlineassembly012";
        Assert.assertEquals(68, run(file));
    }

    @Test
    public void test013() {
        String file = "inlineassembly013";
        Assert.assertEquals(255, run(file));
    }

    @Test
    public void test014() {
        String file = "inlineassembly014";
        Assert.assertEquals(172, run(file));
    }

    @Test
    public void test015() {
        String file = "inlineassembly015";
        Assert.assertEquals(2, run(file));
    }

    @Test
    public void test016() {
        String file = "inlineassembly016";
        Assert.assertEquals(172, run(file));
    }

    @Test
    public void test017() {
        String file = "inlineassembly017";
        Assert.assertEquals(-1, run(file));
    }

    private static int run(String fileName) {
        Builder builder = PolyglotEngine.newBuilder();
        builder.globalSymbol(null, null);
        final PolyglotEngine engine = builder.build();
        try {
            File cFile = new File(PATH, fileName + ".c");
// File bcFile = new File(PATH + "/", "bc_" + fileName + ".ll");
// File bcOptFile = new File(PATH + "/", "bcopt_" + fileName + ".ll");
            File bcFile = File.createTempFile(PATH + "/" + "bc_" + fileName, ".ll");
            File bcOptFile = File.createTempFile(PATH + "/" + "bcopt_" + fileName, ".ll");
            Clang.compileToLLVMIR(cFile, bcFile, ClangOptions.builder());
            Opt.optimizeBitcodeFile(bcFile, bcOptFile, OptOptions.builder().pass(Pass.MEM_TO_REG));
            return engine.eval(Source.fromFileName(bcOptFile.getPath())).as(Integer.class);
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            engine.dispose();
        }
    }
}
