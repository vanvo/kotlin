enum class E {/* KtClass[classId=/E, hasNonLocalFqName=true] */
    A {/* KtEnumEntry[classId=null, hasNonLocalFqName=true] */
        val x: Int = 10/* KtProperty[hasNonLocalFqName=false] */

        typealias B = A/* KtTypeAlias[classId=null, hasNonLocalFqName=false] */

        class Y {/* KtClass[classId=null, hasNonLocalFqName=false] */
            fun x(): Int = 10  /* KtNamedFunction[hasNonLocalFqName=false] */
        }
    }
}