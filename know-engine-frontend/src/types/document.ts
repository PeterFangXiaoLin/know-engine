export type DocumentStatus =
  | 'INIT'
  | 'UPLOADED'
  | 'CONVERTING'
  | 'CONVERTED'
  | 'CHUNKED'
  | 'VECTOR_STORED'

export interface KnowledgeDocument {
  docId: number | string
  docTitle: string
  uploadUser: string
  docUrl: string
  convertedDocUrl?: string | null
  status: DocumentStatus
  accessibleBy?: string | null
  extension?: string | null
}

export interface UploadRecord {
  id: string
  fileName: string
  size: number
  uploadedAt: Date
  document: KnowledgeDocument
}
