package com.microsoft.java.lombok;

import java.util.Set;
import java.util.Arrays;
import java.util.HashSet;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.manipulation.CoreASTProvider;
import org.eclipse.jdt.internal.corext.dom.ASTNodes;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.internal.corext.codemanipulation.GetterSetterUtil;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorField;
import org.eclipse.jdt.ls.core.internal.codemanipulation.GenerateGetterSetterOperation.AccessorKind;
import org.eclipse.jdt.ls.core.internal.text.correction.SourceAssistProcessor;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.lsp4j.CodeActionParams;
import org.eclipse.text.edits.MultiTextEdit;
import org.eclipse.text.edits.TextEdit;

import com.microsoft.java.lombok.ConstructorHandler.ConstructorKind;

public class DataHandler {
    private static final String lombokCanEqualMethod = "canEqual";
    private static final String lombokEqualsMethod = "equals";
    private static final String lombokHashCodeMethod = "hashCode";
    private static final String lombokToStringMethod = "toString";

    public static TextEdit generateDataTextEdit(CodeActionParams params, IProgressMonitor monitor) {
        IType type = SourceAssistProcessor.getSelectionType(params, monitor);
        if (type == null || type.getCompilationUnit() == null) {
			return null;
		}
        
        TextEdit textEdit = new MultiTextEdit();
        TextEdit allArgsConstructorTextEdit = ConstructorHandler.generateConstructor(params, monitor, ConstructorKind.ALLARGSCONSTRUCTOR);
        if (allArgsConstructorTextEdit != null) {
            textEdit.addChild(allArgsConstructorTextEdit);
        }
        TextEdit noArgConstructorTextEdit = ConstructorHandler.generateConstructor(params, monitor, ConstructorKind.NOARGCONSTRUCTOR);
        if (noArgConstructorTextEdit != null) {
            textEdit.addChild(noArgConstructorTextEdit);
        }
        TextEdit accessorsTextEdit = GetterSetterHandler.generateGetterSetter(params, monitor);
        if (accessorsTextEdit != null) {
            textEdit.addChild(accessorsTextEdit);
        }
        TextEdit toStringTextEdit = ToStringHandler.generateToString(params, monitor);
        if (toStringTextEdit != null) {
            textEdit.addChild(toStringTextEdit);
        }
        TextEdit hashCodeEqualsTextEdit = EqualsAndHashCodeHandler.generateHashCodeEquals(params, monitor);
        if (hashCodeEqualsTextEdit != null) {
            textEdit.addChild(hashCodeEqualsTextEdit);
        }
        
        return textEdit;
    }

    public static void removeMethods(IType type, ListRewrite rewriter, IProgressMonitor monitor){
        try{
            CompilationUnit astRoot = CoreASTProvider.getInstance().getAST(type.getCompilationUnit(), CoreASTProvider.WAIT_YES, monitor);
            if (astRoot == null) {
		    	return;
            }
            ITypeBinding typeBinding = ASTNodes.getTypeBinding(astRoot, type);
		    if (typeBinding == null) {
		    	return;
		    }
            
            Set<String> dataMethods = new HashSet<String>(Arrays.asList(lombokCanEqualMethod, lombokEqualsMethod, lombokHashCodeMethod, lombokToStringMethod));
            String className = typeBinding.getName();
            dataMethods.add(className);
            AccessorField[] accessors = GetterSetterHandler.getimplementedAccessors(type, AccessorKind.BOTH);
            for(AccessorField accessor : accessors) {
                IField field = type.getField(accessor.fieldName);
                String getterName = GetterSetterUtil.getGetterName(field, null);
                String setterName = GetterSetterUtil.getSetterName(field, null);
                dataMethods.add(getterName);
                dataMethods.add(setterName);
            }
            IMethodBinding[] declaredMethods = typeBinding.getDeclaredMethods();
            for(IMethodBinding item : declaredMethods){
                if (item.isDefaultConstructor()) {
                    continue;
                }
                if(dataMethods.contains(item.getName())){
                    item.getName();
                    ASTNode node = astRoot.findDeclaringNode(item);
                    rewriter.replace(node, null, null);
                }
            }
        } catch (Exception e) {
			JavaLanguageServerPlugin.logException("Remove Lombok @Data methods", e);
		}
        return;
    }
}