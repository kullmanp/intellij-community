package com.intellij.lang.ant.psi.introspection.impl;

import com.intellij.lang.ant.misc.AntStringInterner;
import com.intellij.lang.ant.psi.impl.AntFileImpl;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AntTypeDefinitionImpl implements AntTypeDefinition {

  private static final AntTypeId ourJavadocId = new AntTypeId(AntFileImpl.JAVADOC_TAG);
  private static final AntTypeId ourUnzipId = new AntTypeId(AntFileImpl.UNZIP_TAG);

  private AntTypeId myTypeId;
  private String myClassName;
  private boolean myIsTask;
  private PsiElement myDefiningElement;
  /**
   * Attribute names to their types.
   */
  private final Map<String, AntAttributeType> myAttributes;
  private String[] myAttributesArray;
  /**
   * Task ids to their class names.
   */
  private final Map<AntTypeId, String> myNestedClassNames;
  private AntTypeId[] myNestedElementsArray;

  public AntTypeDefinitionImpl(final AntTypeDefinitionImpl base) {
    this(base.getTypeId(), base.getClassName(), base.isTask(), new HashMap<String, AntAttributeType>(base.myAttributes),
         new HashMap<AntTypeId, String>(base.myNestedClassNames));
  }

  public AntTypeDefinitionImpl(final AntTypeId id, final String className, final boolean isTask) {
    this(id, className, isTask, new HashMap<String, AntAttributeType>(), new HashMap<AntTypeId, String>());
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask,
                               @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements) {
    this(id, className, isTask, attributes, nestedElements, null);
  }

  public AntTypeDefinitionImpl(final AntTypeId id,
                               final String className,
                               final boolean isTask,
                               @NonNls @NotNull final Map<String, AntAttributeType> attributes,
                               final Map<AntTypeId, String> nestedElements,
                               final PsiElement definingElement) {
    myTypeId = id;
    setClassName(className);
    myIsTask = isTask;
    attributes.put(AntFileImpl.ID_ATTR, AntAttributeType.STRING);
    myAttributes = attributes;
    myNestedClassNames = nestedElements;
    myDefiningElement = definingElement;
  }

  public final AntTypeId getTypeId() {
    return myTypeId;
  }

  public final void setTypeId(final AntTypeId id) {
    myTypeId = id;
  }

  public final String getClassName() {
    return myClassName;
  }

  public final boolean isTask() {
    return myIsTask;
  }

  public final String[] getAttributes() {
    if (myAttributesArray == null || myAttributesArray.length != myAttributes.size()) {
      myAttributesArray = myAttributes.keySet().toArray(new String[myAttributes.size()]);
    }
    return myAttributesArray;
  }

  public final AntAttributeType getAttributeType(final String attr) {
    for (int i = 0; i < attr.length(); ++i) {
      if (!Character.isLowerCase(attr.charAt(i))) {
        return myAttributes.get(attr.toLowerCase(Locale.US));
      }
    }
    return myAttributes.get(attr);
  }

  public final Map<String, AntAttributeType> getAttributesMap() {
    return myAttributes;
  }

  @SuppressWarnings({"unchecked"})
  public final AntTypeId[] getNestedElements() {
    if (myNestedElementsArray == null || myNestedElementsArray.length != myNestedClassNames.size()) {
      myNestedElementsArray = myNestedClassNames.keySet().toArray(new AntTypeId[myNestedClassNames.size()]);
    }
    return myNestedElementsArray;
  }

  public final Map<AntTypeId, String> getNestedElementsMap() {
    return myNestedClassNames;
  }

  @Nullable
  public final String getNestedClassName(final AntTypeId id) {
    final String nsPrefix = id.getNamespacePrefix();
    if (nsPrefix == null) {
      final String name = id.getName();
      /**
       * Hardcode for <javadoc> task (IDEADEV-6731).
       */
      if (name.equals(AntFileImpl.JAVADOC2_TAG)) {
        return myNestedClassNames.get(ourJavadocId);
      }
      /**
       * Hardcode for <unwar> and <unjar> tasks (IDEADEV-6830).
       */
      if (name.equals(AntFileImpl.UNWAR_TAG) || name.equals(AntFileImpl.UNJAR_TAG)) {
        return myNestedClassNames.get(ourUnzipId);
      }
    }
    return myNestedClassNames.get(id);
  }

  public final void registerNestedType(final AntTypeId id, String taskClassName) {
    myNestedClassNames.put(id, taskClassName);
  }

  public final void unregisterNestedType(final AntTypeId typeId) {
    myNestedClassNames.remove(typeId);
  }

  public final PsiElement getDefiningElement() {
    return myDefiningElement;
  }

  public final void setDefiningElement(final PsiElement element) {
    myDefiningElement = element;
  }

  public final void setIsTask(final boolean isTask) {
    myIsTask = isTask;
  }

  public final void setClassName(final String className) {
    myClassName = AntStringInterner.intern(className);
  }
}
