import type { NextFunction, Request, Response } from 'express';
import type { Logger } from 'pino';
import { ZodError } from 'zod';
import { AppError, Errors } from '../utils/errors';

export function notFoundHandler(_req: Request, _res: Response, next: NextFunction) {
  next(Errors.notFound('Endpoint tidak ditemukan.'));
}

/** Converts every error into the standard envelope. Stack traces and internals never reach clients. */
export function errorHandler(logger: Logger) {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  return (err: unknown, req: Request, res: Response, _next: NextFunction) => {
    let appErr: AppError;
    if (err instanceof AppError) {
      appErr = err;
    } else if (err instanceof ZodError) {
      appErr = Errors.invalidInput(
        'Permintaan tidak valid.',
        err.issues.map((i) => ({ field: i.path.join('.'), message: i.message })),
      );
    } else if ((err as { type?: string })?.type === 'entity.parse.failed') {
      appErr = Errors.invalidInput('Body JSON tidak valid.');
    } else if ((err as { type?: string })?.type === 'entity.too.large') {
      appErr = new AppError(413, 'INVALID_INPUT', 'Body terlalu besar.');
    } else {
      appErr = Errors.internal();
    }

    const logPayload = {
      requestId: req.requestId,
      status: appErr.status,
      code: appErr.code,
      method: req.method,
      path: req.path,
    };
    if (appErr.status >= 500) {
      logger.error({ ...logPayload, err: err instanceof Error ? { name: err.name, message: err.message, stack: err.stack } : String(err) }, 'Request failed');
    } else {
      logger.info(logPayload, 'Request rejected');
    }

    res.status(appErr.status).json({
      success: false,
      error: {
        code: appErr.code,
        message: appErr.message,
        ...(appErr.details !== undefined ? { details: appErr.details } : {}),
      },
      requestId: req.requestId,
    });
  };
}
