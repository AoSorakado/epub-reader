const BACKGROUND_IMAGE_DB_NAME = 'liquid-glass-minimal-demo'
const BACKGROUND_IMAGE_STORE_NAME = 'sdf-overlap-background'
const BACKGROUND_IMAGE_KEY = 'background-image'

export type StoredBackgroundImage = {
  blob: Blob
  name: string
}

function openBackgroundImageDb() {
  return new Promise<IDBDatabase>((resolve, reject) => {
    const request = indexedDB.open(BACKGROUND_IMAGE_DB_NAME, 1)

    request.onupgradeneeded = () => {
      request.result.createObjectStore(BACKGROUND_IMAGE_STORE_NAME)
    }
    request.onsuccess = () => resolve(request.result)
    request.onerror = () => reject(request.error)
  })
}

async function withBackgroundImageStore<T>(
  mode: IDBTransactionMode,
  callback: (store: IDBObjectStore) => IDBRequest<T>,
) {
  const db = await openBackgroundImageDb()

  return new Promise<T>((resolve, reject) => {
    const transaction = db.transaction(BACKGROUND_IMAGE_STORE_NAME, mode)
    const request = callback(transaction.objectStore(BACKGROUND_IMAGE_STORE_NAME))
    let result: T | undefined

    request.onsuccess = () => {
      result = request.result
    }
    request.onerror = () => {
      db.close()
      reject(request.error)
    }
    transaction.oncomplete = () => {
      db.close()
      resolve(result as T)
    }
    transaction.onerror = () => {
      db.close()
      reject(transaction.error)
    }
    transaction.onabort = () => {
      db.close()
      reject(transaction.error)
    }
  })
}

export function loadStoredBackgroundImage() {
  return withBackgroundImageStore<StoredBackgroundImage | undefined>('readonly', (store) =>
    store.get(BACKGROUND_IMAGE_KEY) as IDBRequest<StoredBackgroundImage | undefined>,
  )
}

export function saveStoredBackgroundImage(blob: Blob, name: string) {
  return withBackgroundImageStore<IDBValidKey>('readwrite', (store) =>
    store.put({ blob, name }, BACKGROUND_IMAGE_KEY),
  )
}

export function deleteStoredBackgroundImage() {
  return withBackgroundImageStore<undefined>('readwrite', (store) =>
    store.delete(BACKGROUND_IMAGE_KEY) as IDBRequest<undefined>,
  )
}
