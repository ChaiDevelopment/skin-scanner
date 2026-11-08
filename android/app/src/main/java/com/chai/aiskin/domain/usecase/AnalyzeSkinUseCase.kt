package com.chai.aiskin.domain.usecase

import com.chai.aiskin.core.common.AppError
import com.chai.aiskin.core.common.AppResult
import com.chai.aiskin.core.constants.AppConstants
import com.chai.aiskin.domain.model.SkinAnalysis
import com.chai.aiskin.domain.repository.AnalysisRepository
import java.io.File
import javax.inject.Inject

/** Guards the upload (file present, size limit) before handing it to the repository. */
class AnalyzeSkinUseCase @Inject constructor(private val repository: AnalysisRepository) {
    suspend operator fun invoke(image: File, storePhoto: Boolean): AppResult<SkinAnalysis> {
        if (!image.exists() || image.length() == 0L) {
            return AppResult.Failure(AppError.Validation("Foto tidak ditemukan. Silakan ambil ulang foto."))
        }
        if (image.length() > AppConstants.UPLOAD_MAX_BYTES) {
            return AppResult.Failure(AppError.ImageTooLarge())
        }
        return repository.analyze(image, storePhoto)
    }
}
