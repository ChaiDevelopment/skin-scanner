export type ErrorCode =
  | 'INVALID_INPUT'
  | 'INVALID_IMAGE'
  | 'UNAUTHENTICATED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'IMAGE_TOO_LARGE'
  | 'UNSUPPORTED_MEDIA_TYPE'
  | 'INSUFFICIENT_IMAGE_QUALITY'
  | 'RATE_LIMITED'
  | 'INTERNAL_ERROR'
  | 'AI_PROVIDER_ERROR'
  | 'AI_INVALID_RESPONSE'
  | 'AI_TIMEOUT'
  | 'AI_QUOTA_EXCEEDED'
  | 'SERVICE_UNAVAILABLE'
  | 'NOT_CONFIGURED';

/** An error that is safe to show to API clients. Never put secrets or stack traces in `message`/`details`. */
export class AppError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: ErrorCode,
    message: string,
    public readonly details?: unknown,
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export const Errors = {
  invalidInput: (message = 'Permintaan tidak valid.', details?: unknown) =>
    new AppError(400, 'INVALID_INPUT', message, details),
  invalidImage: (message = 'Gambar tidak dapat dibaca.') => new AppError(400, 'INVALID_IMAGE', message),
  unauthenticated: (message = 'Autentikasi diperlukan.') => new AppError(401, 'UNAUTHENTICATED', message),
  forbidden: (message = 'Akses ditolak.') => new AppError(403, 'FORBIDDEN', message),
  notFound: (message = 'Data tidak ditemukan.') => new AppError(404, 'NOT_FOUND', message),
  imageTooLarge: (maxMb: number) =>
    new AppError(413, 'IMAGE_TOO_LARGE', `Ukuran gambar melebihi batas ${maxMb} MB.`),
  unsupportedMedia: (message = 'Format gambar tidak didukung. Gunakan JPEG, PNG, atau WebP.') =>
    new AppError(415, 'UNSUPPORTED_MEDIA_TYPE', message),
  insufficientQuality: (details: unknown) =>
    new AppError(
      422,
      'INSUFFICIENT_IMAGE_QUALITY',
      'Kualitas foto belum cukup untuk dianalisis. Silakan ambil ulang foto.',
      details,
    ),
  rateLimited: () =>
    new AppError(429, 'RATE_LIMITED', 'Terlalu banyak permintaan. Silakan coba lagi beberapa saat lagi.'),
  internal: () => new AppError(500, 'INTERNAL_ERROR', 'Terjadi kesalahan pada server.'),
  aiProvider: (message = 'Layanan AI sedang bermasalah. Silakan coba lagi.') =>
    new AppError(502, 'AI_PROVIDER_ERROR', message),
  aiInvalidResponse: () =>
    new AppError(502, 'AI_INVALID_RESPONSE', 'Respons AI tidak valid. Silakan coba lagi.'),
  aiQuota: () =>
    new AppError(503, 'AI_QUOTA_EXCEEDED', 'Kuota layanan AI sedang habis. Silakan coba lagi nanti.'),
  aiTimeout: () =>
    new AppError(504, 'AI_TIMEOUT', 'Analisis AI melebihi batas waktu. Silakan coba lagi.'),
  unavailable: (message = 'Layanan sedang tidak tersedia.') =>
    new AppError(503, 'SERVICE_UNAVAILABLE', message),
  notConfigured: (what: string) =>
    new AppError(503, 'NOT_CONFIGURED', `${what} belum dikonfigurasi di server.`),
};
