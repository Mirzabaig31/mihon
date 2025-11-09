package eu.kanade.presentation.util

import android.content.Context
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.util.system.isOnline
import tachiyomi.core.common.i18n.stringResource
import tachiyomi.data.source.NoResultsException
import tachiyomi.domain.source.model.SourceNotInstalledException
import tachiyomi.i18n.MR
import java.net.UnknownHostException

context(ctx: Context)
val Throwable.formattedMessage: String
    get() {
        when (this) {
            is HttpException -> return ctx.stringResource(MR.strings.exception_http, code)
            is UnknownHostException -> {
                return if (!ctx.isOnline()) {
                    ctx.stringResource(MR.strings.exception_offline)
                } else {
                    ctx.stringResource(MR.strings.exception_unknown_host, message ?: "")
                }
            }

            is NoResultsException -> return ctx.stringResource(MR.strings.no_results_found)
            is SourceNotInstalledException -> return ctx.stringResource(MR.strings.loader_not_implemented_error)
        }
        return when (val className = this::class.simpleName) {
            "Exception", "IOException" -> message ?: className
            else -> "$className: $message"
        }
    }
