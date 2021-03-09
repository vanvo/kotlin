class A() {
  constructor(x: Int): this()
  constructor(y: Int, z: String) : this(y)
}

// SYMBOLS:
KtFirConstructorSymbol:
  annotatedType: [] A
  annotationClassIds: []
  annotations: []
  containingClassIdIfNonLocal: A
  dispatchType: null
  isPrimary: true
  origin: SOURCE
  symbolKind: MEMBER
  typeParameters: []
  valueParameters: []
  visibility: PUBLIC

KtFirConstructorValueParameterSymbol:
  annotatedType: [] kotlin/Int
  annotationClassIds: []
  annotations: []
  constructorParameterKind: VAL_PROPERTY
  hasDefaultValue: false
  isVararg: false
  name: x
  origin: SOURCE
  symbolKind: MEMBER

KtFirConstructorSymbol:
  annotatedType: [] A
  annotationClassIds: []
  annotations: []
  containingClassIdIfNonLocal: A
  dispatchType: null
  isPrimary: false
  origin: SOURCE
  symbolKind: MEMBER
  typeParameters: []
  valueParameters: [KtFirConstructorValueParameterSymbol(x)]
  visibility: PUBLIC

KtFirConstructorValueParameterSymbol:
  annotatedType: [] kotlin/Int
  annotationClassIds: []
  annotations: []
  constructorParameterKind: VAL_PROPERTY
  hasDefaultValue: false
  isVararg: false
  name: y
  origin: SOURCE
  symbolKind: MEMBER

KtFirConstructorValueParameterSymbol:
  annotatedType: [] kotlin/String
  annotationClassIds: []
  annotations: []
  constructorParameterKind: VAL_PROPERTY
  hasDefaultValue: false
  isVararg: false
  name: z
  origin: SOURCE
  symbolKind: MEMBER

KtFirConstructorSymbol:
  annotatedType: [] A
  annotationClassIds: []
  annotations: []
  containingClassIdIfNonLocal: A
  dispatchType: null
  isPrimary: false
  origin: SOURCE
  symbolKind: MEMBER
  typeParameters: []
  valueParameters: [KtFirConstructorValueParameterSymbol(y), KtFirConstructorValueParameterSymbol(z)]
  visibility: PUBLIC

KtFirClassOrObjectSymbol:
  annotationClassIds: []
  annotations: []
  classIdIfNonLocal: A
  classKind: CLASS
  companionObject: null
  isData: false
  isExternal: false
  isFun: false
  isInline: false
  isInner: false
  modality: FINAL
  name: A
  origin: SOURCE
  superTypes: [[] kotlin/Any]
  symbolKind: TOP_LEVEL
  typeParameters: []
  visibility: PUBLIC
