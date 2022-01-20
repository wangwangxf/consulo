/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.intellij.plugins.intelliLang.inject.config.ui;

import java.awt.BorderLayout;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.JTextField;

import org.intellij.plugins.intelliLang.inject.config.BaseInjection;
import org.intellij.plugins.intelliLang.inject.config.InjectionPlace;
import consulo.document.Document;
import com.intellij.openapi.editor.ex.EditorEx;
import consulo.virtualFileSystem.fileType.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.UnknownFileType;
import consulo.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.compiler.PatternCompiler;
import com.intellij.patterns.compiler.PatternCompilerImpl;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.ui.EditorTextField;

/**
 * @author Gregory.Shrago
 */
public class BaseInjectionPanel extends AbstractInjectionPanel<BaseInjection>
{

	// read by reflection
	LanguagePanel myLanguagePanel;
	JPanel myCenterPanel;
	EditorTextField myTextArea;
	AdvancedPanel myAdvancedPanel;

	private JPanel myRoot;
	private JTextField myNameTextField;
	private PatternCompiler<PsiElement> myHelper;

	public BaseInjectionPanel(BaseInjection injection, Project project)
	{
		super(injection, project);
		myHelper = injection.getCompiler();
		final FileType groovy = FileTypeManager.getInstance().getFileTypeByExtension("groovy");
		final FileType realFileType = groovy == UnknownFileType.INSTANCE ? PlainTextFileType.INSTANCE : groovy;
		final PsiFile psiFile = PsiFileFactory.getInstance(project).createFileFromText("injection." + realFileType.getDefaultExtension(), realFileType, "", 0, true);
		final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
		psiFile.putUserData(BaseInjection.INJECTION_KEY, injection);
		myTextArea = new EditorTextField(document, project, realFileType)
		{
			@Override
			protected EditorEx createEditor()
			{
				final EditorEx ex = super.createEditor();
				ex.setVerticalScrollbarVisible(true);
				ex.setHorizontalScrollbarVisible(true);
				return ex;
			}
		};
		myTextArea.setOneLineMode(false);
		myCenterPanel.add(myTextArea, BorderLayout.CENTER);
		myTextArea.setFontInheritedFromLAF(false);
		//myTextArea.setFont(EditorColorsManager.getInstance().getGlobalScheme().getFont(EditorFontType.PLAIN));
		init(injection.copy());
	}

	@Override
	protected void apply(BaseInjection other)
	{
		final String displayName = myNameTextField.getText();
		if(StringUtil.isEmpty(displayName))
		{
			throw new IllegalArgumentException("Display name should not be empty");
		}
		other.setDisplayName(displayName);
		boolean enabled = true;
		final StringBuilder sb = new StringBuilder();
		final ArrayList<InjectionPlace> places = new ArrayList<InjectionPlace>();
		for(String s : myTextArea.getText().split("\\s*\n\\s*"))
		{
			final boolean nextEnabled;
			if(s.startsWith("+"))
			{
				nextEnabled = true;
				s = s.substring(1).trim();
			}
			else if(s.startsWith("-"))
			{
				nextEnabled = false;
				s = s.substring(1).trim();
			}
			else
			{
				sb.append(s.trim());
				continue;
			}
			if(sb.length() > 0)
			{
				final String text = sb.toString();
				places.add(new InjectionPlace(myHelper.compileElementPattern(text), enabled));
				sb.setLength(0);
			}
			sb.append(s);
			enabled = nextEnabled;
		}
		if(sb.length() > 0)
		{
			final String text = sb.toString();
			places.add(new InjectionPlace(myHelper.compileElementPattern(text), enabled));
		}
		for(InjectionPlace place : places)
		{
			ElementPattern<PsiElement> pattern = place.getElementPattern();
			if(pattern instanceof PatternCompilerImpl.LazyPresentablePattern)
			{
				try
				{
					((PatternCompilerImpl.LazyPresentablePattern) pattern).compile();
				}
				catch(Throwable ex)
				{
					throw (RuntimeException) new IllegalArgumentException("Pattern failed to compile:").initCause(ex);
				}
			}
		}
		other.setInjectionPlaces(places.toArray(new InjectionPlace[places.size()]));
	}

	@Override
	protected void resetImpl()
	{
		final StringBuilder sb = new StringBuilder();
		for(InjectionPlace place : getOrigInjection().getInjectionPlaces())
		{
			sb.append(place.isEnabled() ? "+ " : "- ").append(place.getText()).append("\n");
		}
		myTextArea.setText(sb.toString());
		myNameTextField.setText(getOrigInjection().getDisplayName());
	}

	@Override
	public JPanel getComponent()
	{
		return myRoot;
	}

	private void createUIComponents()
	{
		myLanguagePanel = new LanguagePanel(getProject(), getOrigInjection());
		myAdvancedPanel = new AdvancedPanel(getProject(), getOrigInjection());
	}
}

