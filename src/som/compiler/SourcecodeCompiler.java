/**
 * Copyright (c) 2009 Michael Haupt, michael.haupt@hpi.uni-potsdam.de
 * Software Architecture Group, Hasso Plattner Institute, Potsdam, Germany
 * http://www.hpi.uni-potsdam.de/swa/
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package som.compiler;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.source.Source;

import som.VM;
import som.compiler.Lexer.SourceCoordinate;
import som.compiler.MixinBuilder.MixinDefinitionError;
import som.compiler.Parser.ParseError;
import som.interpreter.SomLanguage;
import tools.dym.profiles.StructuralProbe;

public final class SourcecodeCompiler {

  @TruffleBoundary
  public static MixinDefinition compileModule(final File file)
      throws IOException, ParseError, MixinDefinitionError {
    FileReader stream = new FileReader(file);

    Source source = Source.newBuilder(file).mimeType(SomLanguage.MIME_TYPE).build();
    Parser parser = new Parser(stream, file.length(), source, VM.getStructuralProbe());
    return compile(parser, source);
  }

  public static MixinDefinition compileModule(final Source source) throws ParseError, MixinDefinitionError {
    return compileModule(source, VM.getStructuralProbe());
  }

  public static MixinDefinition compileModule(final Source source,
      final StructuralProbe structuralProbe) throws ParseError, MixinDefinitionError {
    Parser parser = new Parser(
        source.getReader(), source.getLength(), source, structuralProbe);
    return compile(parser, source);
  }

  private static MixinDefinition compile(final Parser parser,
      final Source source) throws ParseError, MixinDefinitionError {
    SourceCoordinate coord = parser.getCoordinate();
    MixinBuilder mxnBuilder = parser.moduleDeclaration();
    MixinDefinition result = mxnBuilder.assemble(parser.getSource(coord));
    VM.reportLoadedSource(source);
    return result;
  }
}
