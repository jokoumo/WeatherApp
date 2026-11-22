package com.example.weatherapp.data.repository

import com.example.weatherapp.domain.model.DataLoadFailure
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/** Maps transport level problems to a failure the UI can describe in German. */
fun mapIoFailure(throwable: Throwable): DataLoadFailure = when (throwable) {
    is SocketTimeoutException -> DataLoadFailure.TIMEOUT
    is UnknownHostException, is ConnectException, is SSLException -> DataLoadFailure.NETWORK
    is IOException -> DataLoadFailure.NETWORK
    else -> DataLoadFailure.UNKNOWN
}

/** Maps HTTP status codes of the Open-Meteo endpoints to a failure reason. */
fun mapHttpFailure(statusCode: Int): DataLoadFailure = when {
    statusCode == 429 -> DataLoadFailure.RATE_LIMITED
    statusCode == 400 || statusCode == 404 || statusCode == 422 -> DataLoadFailure.INVALID_DATA
    statusCode in 500..599 -> DataLoadFailure.SERVER
    else -> DataLoadFailure.UNKNOWN
}
