/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package consulo.ide.impl.psi.impl.source.codeStyle.lineIndent;

import consulo.language.codeStyle.CodeStyle;
import consulo.language.codeStyle.Indent;
import consulo.language.codeStyle.internal.IndentImpl;
import consulo.language.codeStyle.internal.IndentInfo;
import consulo.language.Language;
import consulo.document.Document;
import consulo.codeEditor.Editor;
import consulo.project.Project;
import consulo.language.psi.PsiDocumentManager;
import consulo.language.psi.PsiFile;
import consulo.language.codeStyle.CommonCodeStyleSettings;
import consulo.ide.impl.psi.impl.source.codeStyle.SemanticEditorPosition;
import consulo.ide.impl.idea.util.text.CharArrayUtil;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import static consulo.language.codeStyle.Indent.Type.*;

public class IndentCalculator {

  private
  @Nonnull
  final Project myProject;
  private
  @Nonnull
  final Editor myEditor;
  private
  @Nonnull
  final BaseLineOffsetCalculator myBaseLineOffsetCalculator;
  private
  @Nonnull
  final Indent myIndent;

  public IndentCalculator(@Nonnull Project project, @Nonnull Editor editor, @Nonnull BaseLineOffsetCalculator baseLineOffsetCalculator, @Nonnull Indent indent) {
    myProject = project;
    myEditor = editor;
    myBaseLineOffsetCalculator = baseLineOffsetCalculator;
    myIndent = indent;
  }

  public final static BaseLineOffsetCalculator LINE_BEFORE = new BaseLineOffsetCalculator() {
    @Override
    public int getOffsetInBaseIndentLine(@Nonnull SemanticEditorPosition currPosition) {
      return CharArrayUtil.shiftBackward(currPosition.getChars(), currPosition.getStartOffset(), " \t\n\r");
    }
  };

  public final static BaseLineOffsetCalculator LINE_AFTER = new BaseLineOffsetCalculator() {
    @Override
    public int getOffsetInBaseIndentLine(@Nonnull SemanticEditorPosition currPosition) {
      return CharArrayUtil.shiftForward(currPosition.getChars(), currPosition.getStartOffset(), " \t\n\r");
    }
  };

  @Nullable
  String getIndentString(@Nullable Language language, @Nonnull SemanticEditorPosition currPosition) {
    String baseIndent = getBaseIndent(currPosition);
    Document document = myEditor.getDocument();
    PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(document);
    if (file != null) {
      CommonCodeStyleSettings.IndentOptions fileOptions = CodeStyle.getIndentOptions(file);
      CommonCodeStyleSettings.IndentOptions options =
              !fileOptions.isOverrideLanguageOptions() && language != null && !(language.is(file.getLanguage()) || language.is(Language.ANY)) ? CodeStyle.getLanguageSettings(file, language)
                      .getIndentOptions() : fileOptions;
      if (options != null) {
        return baseIndent + new IndentInfo(0, indentToSize(myIndent, options), 0, false).generateNewWhiteSpace(options);
      }
    }
    return null;
  }

  @Nonnull
  protected String getBaseIndent(@Nonnull SemanticEditorPosition currPosition) {
    CharSequence docChars = myEditor.getDocument().getCharsSequence();
    int offset = currPosition.getStartOffset();
    if (offset > 0) {
      int indentLineOffset = myBaseLineOffsetCalculator.getOffsetInBaseIndentLine(currPosition);
      if (indentLineOffset > 0) {
        int indentStart = CharArrayUtil.shiftBackwardUntil(docChars, indentLineOffset, "\n") + 1;
        if (indentStart >= 0) {
          int indentEnd = CharArrayUtil.shiftForward(docChars, indentStart, " \t");
          if (indentEnd > indentStart) {
            return docChars.subSequence(indentStart, indentEnd).toString();
          }
        }
      }
    }
    return "";
  }

  private static int indentToSize(@Nonnull Indent indent, @Nonnull CommonCodeStyleSettings.IndentOptions options) {
    if (indent.getType() == NORMAL) {
      return options.INDENT_SIZE;
    }
    else if (indent.getType() == CONTINUATION) {
      return options.CONTINUATION_INDENT_SIZE;
    }
    else if (indent.getType() == SPACES && indent instanceof IndentImpl) {
      return ((IndentImpl)indent).getSpaces();
    }
    return 0;
  }


  public interface BaseLineOffsetCalculator {
    int getOffsetInBaseIndentLine(@Nonnull SemanticEditorPosition position);
  }
}
