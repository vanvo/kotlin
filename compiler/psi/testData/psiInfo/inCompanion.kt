class A {/* KtClass[classId=/A, hasNonLocalFqName=true] */
    companion object {/* KtObjectDeclaration[classId=/A.Companion, hasNonLocalFqName=true] */
        val x: Int = 10/* KtProperty[hasNonLocalFqName=true] */

        class Y {/* KtClass[classId=/A.Companion.Y, hasNonLocalFqName=true] */
            fun x(): Int = 10  /* KtNamedFunction[hasNonLocalFqName=true] */
        }
    }
}