/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the license/LICENSE.txt file.
 */

package org.jetbrains.kotlin.codegen.intrinsics

import org.jetbrains.kotlin.codegen.AsmUtil
import org.jetbrains.kotlin.codegen.ExpressionCodegen
import org.jetbrains.kotlin.codegen.StackValue
import org.jetbrains.kotlin.psi.KtClassLiteralExpression
import org.jetbrains.kotlin.resolve.BindingContext.DOUBLE_COLON_LHS
import org.jetbrains.kotlin.resolve.calls.model.ResolvedCall
import org.jetbrains.kotlin.resolve.jvm.AsmTypes
import org.jetbrains.kotlin.resolve.scopes.receivers.ExpressionReceiver
import org.jetbrains.kotlin.types.TypeUtils
import org.jetbrains.kotlin.types.expressions.DoubleColonLHS
import org.jetbrains.org.objectweb.asm.Type

class KClassJavaPrimitiveTypeProperty : IntrinsicPropertyGetter() {
    override fun generate(resolvedCall: ResolvedCall<*>?, codegen: ExpressionCodegen, returnType: Type, receiver: StackValue): StackValue? {
        val receiverValue = resolvedCall!!.extensionReceiver as? ExpressionReceiver ?: return null
        val classLiteralExpression = receiverValue.expression as? KtClassLiteralExpression ?: return null
        val receiverExpression = classLiteralExpression.receiverExpression ?: return null
        val lhs = codegen.bindingContext.get(DOUBLE_COLON_LHS, receiverExpression) ?: return null
        if (TypeUtils.isTypeParameter(lhs.type)) {
            // TODO: add new operation kind to ReifiedTypeInliner.OperationKind to generate a null value or a field access to TYPE
            return null
        }
        val lhsType = codegen.asmType(lhs.type)
        return StackValue.operation(returnType) { iv ->
            if (lhs is DoubleColonLHS.Expression && !lhs.isObjectQualifier) {
                val receiverStackValue = codegen.gen(receiverExpression)
                val receiverType = receiverStackValue.type
                receiverStackValue.put(receiverType, iv)
                when {
                    receiverType == Type.VOID_TYPE -> {
                        iv.aconst(null)
                    }
                    AsmUtil.isPrimitive(receiverType) -> {
                        AsmUtil.pop(iv, receiverType)
                        iv.getstatic(AsmUtil.boxType(receiverType).internalName, "TYPE", "Ljava/lang/Class;")
                    }
                    else -> {
                        if (AsmUtil.unboxPrimitiveTypeOrNull(receiverType) != null) {
                            AsmUtil.pop(iv, receiverType)
                            iv.getstatic(receiverType.internalName, "TYPE", "Ljava/lang/Class;")
                        } else {
                            if (receiverType == AsmTypes.OBJECT_TYPE || receiverType == AsmTypes.NUMBER_TYPE) {
                                iv.invokevirtual("java/lang/Object", "getClass", "()Ljava/lang/Class;", false)
                                AsmUtil.wrapJavaClassIntoKClass(iv)
                                iv.invokestatic(
                                    "kotlin/jvm/JvmClassMappingKt", "getJavaPrimitiveType",
                                    "(Lkotlin/reflect/KClass;)Ljava/lang/Class;", false
                                )
                            } else {
                                AsmUtil.pop(iv, receiverType)
                                iv.aconst(null)
                            }
                        }
                    }
                }
            } else if (AsmUtil.isPrimitive(lhsType)
                || AsmUtil.unboxPrimitiveTypeOrNull(lhsType) != null
                || AsmTypes.VOID_WRAPPER_TYPE == lhsType
            ) {
                iv.getstatic(AsmUtil.boxType(lhsType).internalName, "TYPE", "Ljava/lang/Class;")
            } else {
                iv.aconst(null)
            }
        }
    }
}
