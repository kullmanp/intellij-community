/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.uiDesigner.actions;

import com.intellij.codeInsight.generation.GenerateMembersUtil;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.SuggestedNameInfo;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.uiDesigner.compiler.Utils;
import com.intellij.uiDesigner.lw.LwRootContainer;
import com.intellij.uiDesigner.UIDesignerBundle;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

/**
 * @author yole
 */
public class GenerateMainAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.uiDesigner.actions.GenerateMainAction");

  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(DataKeys.PROJECT);
    assert project != null;
    final Editor editor = e.getData(DataKeys.EDITOR);
    assert editor != null;
    final int offset = editor.getCaretModel().getOffset();
    final PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    PsiClass psiClass = PsiTreeUtil.getParentOfType(file.findElementAt(offset), PsiClass.class);
    assert psiClass != null;

    if (!PsiUtil.hasDefaultConstructor(psiClass)) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.no.default.constructor"), UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }

    final PsiFile[] boundForms = PsiManager.getInstance(project).getSearchHelper().findFormsBoundToClass(psiClass.getQualifiedName());
    final LwRootContainer rootContainer;
    try {
      rootContainer = Utils.getRootContainer(boundForms [0].getText(), null);
    }
    catch (Exception ex) {
      LOG.error(ex);
      return;
    }

    if (rootContainer.getComponentCount() == 0) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.empty.form"),
                                 UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }
    String rootBinding = rootContainer.getComponent(0).getBinding();
    if (rootBinding == null || psiClass.findFieldByName(rootBinding, true) == null) {
      Messages.showMessageDialog(project, UIDesignerBundle.message("generate.main.no.root.binding"),
                                 UIDesignerBundle.message("generate.main.title"), Messages.getErrorIcon());
      return;
    }

    @NonNls final StringBuilder mainBuilder = new StringBuilder("public static void main(String[] args) { ");
    final CodeStyleManager csm = CodeStyleManager.getInstance(project);
    SuggestedNameInfo nameInfo = csm.suggestVariableName(VariableKind.LOCAL_VARIABLE, "frame", null, null);
    String varName = nameInfo.names [0];
    mainBuilder.append(JFrame.class.getName()).append(" ").append(varName).append("= new ").append(JFrame.class.getName());
    mainBuilder.append("(\"").append(psiClass.getName()).append("\");");
    mainBuilder.append(varName).append(".setContentPane(new ").append(psiClass.getQualifiedName()).append("().").append(rootBinding).append(");");
    mainBuilder.append(varName).append(".setDefaultCloseOperation(").append(JFrame.class.getName()).append(".EXIT_ON_CLOSE);");
    mainBuilder.append(varName).append(".pack();");
    mainBuilder.append(varName).append(".setVisible(true);");

    mainBuilder.append("}\n");

    CommandProcessor.getInstance().executeCommand(project, new Runnable() {
      public void run() {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            try {
              PsiMethod method = file.getManager().getElementFactory().createMethodFromText(mainBuilder.toString(), file);
              Object[] resultMembers = GenerateMembersUtil.insertMembersAtOffset(file, offset, new Object[]{method});
              GenerateMembersUtil.positionCaret(editor, (PsiElement)resultMembers[0], false);
            }
            catch (IncorrectOperationException e1) {
              LOG.error(e1);
            }
          }
        });
      }
    }, null, null);
  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setVisible(isActionEnabled(e));
  }

  private static boolean isActionEnabled(final AnActionEvent e) {
    Project project = e.getData(DataKeys.PROJECT);
    if (project == null) return false;
    Editor editor = e.getData(DataKeys.EDITOR);
    if (editor == null) return false;
    int offset = editor.getCaretModel().getOffset();
    PsiFile file = PsiDocumentManager.getInstance(project).getPsiFile(editor.getDocument());
    if (file == null) return false;
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    PsiClass psiClass = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    if (psiClass == null) return false;
    if (ApplicationConfigurationType.findMainMethod(psiClass) != null) return false;
    if (psiClass.getManager().getSearchHelper().findFormsBoundToClass(psiClass.getQualifiedName()).length == 0) return false;
    return true;
  }
}
