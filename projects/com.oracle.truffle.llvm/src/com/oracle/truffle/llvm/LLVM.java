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
package com.oracle.truffle.llvm;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.StringInputStream;

import com.google.inject.Injector;
import com.intel.llvm.ireditor.LLVM_IRStandaloneSetup;
import com.intel.llvm.ireditor.lLVM_IR.Model;
import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.RootCallTarget;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.TruffleLanguage.Env;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.source.MissingNameException;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.api.vm.PolyglotEngine;
import com.oracle.truffle.api.vm.PolyglotEngine.Builder;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMContext;
import com.oracle.truffle.llvm.nodes.impl.base.LLVMLanguage;
import com.oracle.truffle.llvm.parser.LLVMParserResult;
import com.oracle.truffle.llvm.parser.bc.impl.LLVMBitcodeVisitor;
import com.oracle.truffle.llvm.parser.factories.NodeFactoryFacadeImpl;
import com.oracle.truffle.llvm.parser.impl.LLVMVisitor;
import com.oracle.truffle.llvm.runtime.LLVMLogger;
import com.oracle.truffle.llvm.runtime.LLVMPropertyOptimizationConfiguration;
import com.oracle.truffle.llvm.runtime.options.LLVMBaseOptionFacade;

/**
 * This is the main LLVM execution class.
 */
public class LLVM {

    static final LLVMPropertyOptimizationConfiguration OPTIMIZATION_CONFIGURATION = new LLVMPropertyOptimizationConfiguration();

    static {
        LLVMLanguage.provider = getProvider();
    }

    private static LLVMLanguage.LLVMLanguageProvider getProvider() {
        return new LLVMLanguage.LLVMLanguageProvider() {

            @Override
            public CallTarget parse(Source code, Node contextNode, String... argumentNames) throws IOException {
                Node findContext = LLVMLanguage.INSTANCE.createFindContextNode0();
                LLVMContext context = LLVMLanguage.INSTANCE.findContext0(findContext);
                parseDynamicBitcodeLibraries(context);
                final CallTarget[] mainFunction = new CallTarget[]{null};
                if (code.getMimeType().equals(LLVMLanguage.LLVM_IR_MIME_TYPE)) {
                    String path = code.getPath();
                    LLVMParserResult parserResult;
                    try {
                        if (path == null) {
                            parserResult = parseString(code, context);
                        } else {
                            parserResult = parseFile(code, context);
                        }
                    } catch (IllegalStateException e) {
                        throw new IOException(e);
                    }
                    mainFunction[0] = parserResult.getMainFunction();
                    handleParserResult(context, parserResult);
                } else if (code.getMimeType().equals(LLVMLanguage.SULONG_LIBRARY_MIME_TYPE)) {
                    final SulongLibrary library = new SulongLibrary(new File(code.getPath()));

                    library.readContents(dependentLibrary -> {
                        context.addLibraryToNativeLookup(dependentLibrary);
                    }, source -> {
                        LLVMParserResult parserResult;
                        try {
                            parserResult = parseString(source, context);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                        handleParserResult(context, parserResult);
                        if (parserResult.getMainFunction() != null) {
                            mainFunction[0] = parserResult.getMainFunction();
                        }
                    });

                    if (mainFunction[0] == null) {
                        mainFunction[0] = Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(null));
                    }
                } else {
                    throw new IllegalArgumentException("undeclared mime type");
                }
                if (context.isParseOnly()) {
                    return Truffle.getRuntime().createCallTarget(RootNode.createConstantNode(mainFunction));
                } else {
                    return mainFunction[0];
                }
            }

            private void parseDynamicBitcodeLibraries(LLVMContext context) {
                String[] dynamicLibraryPaths = LLVMBaseOptionFacade.getDynamicBitcodeLibraries();
                if (dynamicLibraryPaths != null && dynamicLibraryPaths.length != 0) {
                    for (String s : dynamicLibraryPaths) {
                        Source source;
                        try {
                            source = Source.newBuilder(new File(s)).build();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        LLVMParserResult result = parseFile(source, context);
                        handleParserResult(context, result);
                    }
                }
            }

            private void handleParserResult(LLVMContext context, LLVMParserResult result) {
                context.getFunctionRegistry().register(result.getParsedFunctions());
                context.registerStaticInitializer(result.getStaticInits());
                context.registerStaticDestructor(result.getStaticDestructors());
                if (!context.isParseOnly()) {
                    result.getStaticInits().call();
                }
            }

            @Override
            public LLVMContext createContext(Env env) {
                NodeFactoryFacadeImpl facade = new NodeFactoryFacadeImpl();
                LLVMContext context = new LLVMContext(facade, OPTIMIZATION_CONFIGURATION);
                LLVMVisitor runtime = new LLVMVisitor(OPTIMIZATION_CONFIGURATION, context.getMainArguments(), context.getMainSourceFile(), context.getMainSourceFile());
                facade.setParserRuntime(runtime);
                if (env != null) {
                    Object mainArgs = env.getConfig().get(LLVMLanguage.MAIN_ARGS_KEY);
                    if (mainArgs != null) {
                        context.setMainArguments((Object[]) mainArgs);
                    }
                    Object sourceFile = env.getConfig().get(LLVMLanguage.LLVM_SOURCE_FILE_KEY);
                    if (sourceFile != null) {
                        context.setMainSourceFile((Source) sourceFile);
                    }
                    Object parseOnly = env.getConfig().get(LLVMLanguage.PARSE_ONLY_KEY);
                    if (parseOnly != null) {
                        context.setParseOnly((boolean) parseOnly);
                    }
                }
                context.getStack().allocate();
                return context;
            }

            @Override
            public void disposeContext(LLVMContext context) {
                // the PolyglotEngine calls this method for every mime type supported by the
                // language
                if (!context.getStack().isFreed()) {
                    for (RootCallTarget destructor : context.getStaticDestructors()) {
                        destructor.call();
                    }
                    context.getStack().free();
                }
            }
        };
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            throw new IllegalArgumentException("please provide a file to execute!");
        }
        File file = new File(args[0]);
        Object[] otherArgs = new Object[args.length - 1];
        System.arraycopy(args, 1, otherArgs, 0, otherArgs.length);
        int status = executeMain(file, otherArgs);
        System.exit(status);
    }

    public static LLVMParserResult parseString(Source source, LLVMContext context) throws IOException {
        LLVM_IRStandaloneSetup setup = new LLVM_IRStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        Resource resource = resourceSet.createResource(URI.createURI("dummy:/sulong.ll"));
        try (InputStream in = new StringInputStream(source.getCode())) {
            resource.load(in, resourceSet.getLoadOptions());
        }
        EList<EObject> contents = resource.getContents();
        if (contents.size() == 0) {
            throw new IllegalStateException("empty file?");
        }
        Model model = (Model) contents.get(0);
        LLVMVisitor llvmVisitor = new LLVMVisitor(OPTIMIZATION_CONFIGURATION, context.getMainArguments(), source, context.getMainSourceFile());
        return llvmVisitor.getMain(model, new NodeFactoryFacadeImpl(llvmVisitor));
    }

    public static LLVMParserResult parseFile(Source source, LLVMContext context) {
        LLVM_IRStandaloneSetup setup = new LLVM_IRStandaloneSetup();
        Injector injector = setup.createInjectorAndDoEMFRegistration();
        XtextResourceSet resourceSet = injector.getInstance(XtextResourceSet.class);
        resourceSet.addLoadOption(XtextResource.OPTION_RESOLVE_ALL, Boolean.TRUE);
        Resource resource = resourceSet.getResource(URI.createURI(source.getPath()), true);
        EList<EObject> contents = resource.getContents();
        if (contents.size() == 0) {
            throw new IllegalStateException("empty file?");
        }
        Model model = (Model) contents.get(0);
        LLVMVisitor llvmVisitor = new LLVMVisitor(OPTIMIZATION_CONFIGURATION, context.getMainArguments(), source, context.getMainSourceFile());
        return llvmVisitor.getMain(model, new NodeFactoryFacadeImpl(llvmVisitor));
    }

    public static LLVMParserResult parseBitcodeFile(Source source, LLVMContext context) {
        return LLVMBitcodeVisitor.getMain(source, context, OPTIMIZATION_CONFIGURATION);
    }

    public static int executeMain(File file, Object... args) {
        LLVMLogger.info("current file: " + file.getAbsolutePath());
        Source fileSource;
        try {
            fileSource = Source.newBuilder(file).build();
            return evaluateFromSource(fileSource, args);
        } catch (IOException e) {
            throw new AssertionError(e);
        }
    }

    public static int executeMain(String codeString, Object... args) {
        try {
            Source fromText = Source.newBuilder(codeString).mimeType(LLVMLanguage.LLVM_IR_MIME_TYPE).build();
            LLVMLogger.info("current code string: " + codeString);
            return evaluateFromSource(fromText, args);
        } catch (MissingNameException e) {
            throw new AssertionError(e);
        }
    }

    private static int evaluateFromSource(Source fileSource, Object... args) {
        Builder engineBuilder = PolyglotEngine.newBuilder();
        engineBuilder.config(LLVMLanguage.LLVM_IR_MIME_TYPE, LLVMLanguage.MAIN_ARGS_KEY, args);
        engineBuilder.config(LLVMLanguage.LLVM_IR_MIME_TYPE, LLVMLanguage.LLVM_SOURCE_FILE_KEY, fileSource);
        PolyglotEngine vm = engineBuilder.build();
        try {
            Integer result = vm.eval(fileSource).as(Integer.class);
            return result;
        } catch (IOException e) {
            throw new AssertionError(e);
        } finally {
            vm.dispose();
        }
    }

}
