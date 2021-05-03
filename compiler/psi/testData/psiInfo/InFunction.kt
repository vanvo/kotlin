fun a() {/* KtNamedFunction[hasNonLocalFqName=true] */
    val x: Int = 10/* KtProperty[hasNonLocalFqName=false] */

    class Y {/* KtClass[classId=null, hasNonLocalFqName=false] */
        fun x(): Int = 10 /* KtNamedFunction[hasNonLocalFqName=false] */
    }
}