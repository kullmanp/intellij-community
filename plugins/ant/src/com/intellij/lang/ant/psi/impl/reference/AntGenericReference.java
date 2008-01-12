package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.GenericReferenceProvider;
import com.intellij.psi.xml.XmlAttribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class AntGenericReference extends GenericReference implements AntReference {
  private final AntElement myAntElement;
  private final String myText;
  private final TextRange myTextRange;
  private final XmlAttribute myAttribute;

  protected AntGenericReference(final GenericReferenceProvider provider,
                                final AntElement element,
                                final String str,
                                final TextRange textRange,
                                final XmlAttribute attribute) {
    super(provider);
    myAntElement = element;
    myText = str;
    myTextRange = textRange;
    myAttribute = attribute;
  }

  protected AntGenericReference(final GenericReferenceProvider provider, final AntStructuredElement element, final XmlAttribute attr) {
    super(provider);
    myAntElement = element;
    myText = (attr == null) ? element.getSourceElement().getName() : attr.getName();
    int startInElement = (attr == null) ? 1 : attr.getTextRange().getStartOffset() - element.getTextRange().getStartOffset();
    myTextRange = new TextRange(startInElement, myText.length() + startInElement);
    myAttribute = attr;
  }

  protected AntGenericReference(final GenericReferenceProvider provider, final AntStructuredElement element) {
    this(provider, element, null);
  }

  public AntElement getElement() {
    return myAntElement;
  }

  public PsiElement getContext() {
    return null;
  }

  public PsiReference getContextReference() {
    return null;
  }

  public TextRange getRangeInElement() {
    return myTextRange;
  }

  public String getCanonicalText() {
    return myText;
  }

  public boolean shouldBeSkippedByAnnotator() {
    return false;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {}  

  @NotNull
  public IntentionAction[] getFixes() {
    return EMPTY_INTENTIONS;
  }

  protected XmlAttribute getAttribute() {
    return myAttribute;
  }

  @Nullable
  public String getCanonicalRepresentationText() {
    final AntElement element = getElement();
    final String value = getCanonicalText();
    if( element instanceof AntStructuredElement) {
      return ((AntStructuredElement)element).computeAttributeValue(value);
    }
    return element.getAntProject().computeAttributeValue(value);
  }

  @Override
  public PsiElement resolveInner() {
    throw new UnsupportedOperationException();
  }

  public String getUnresolvedMessagePattern() {
    return CodeInsightBundle.message("error.cannot.resolve.default.message");
  }
}