package com.mincor.kodiexample.domain.usecases.movies

import com.mincor.kodiexample.common.Consts
import com.mincor.kodiexample.data.dto.SResult
import com.mincor.kodiexample.data.model.local.MovieEntity
import com.mincor.kodiexample.domain.usecases.base.IUseCase
import com.mincor.kodiexample.domain.usecases.details.IGetLocalDetailsUseCase
import com.mincor.kodiexample.domain.usecases.details.IGetRemoteDetailsUseCase
import com.rasalexman.coroutinesmanager.AsyncTasksManager
import com.rasalexman.coroutinesmanager.doAsyncAwait
import com.rasalexman.kodi.annotations.BindProvider

@BindProvider(
        toClass = IGetMovieDetailUseCase::class,
        toModule = Consts.Modules.UCMoviesName
)
class GetMovieDetailUseCase(
        private val getLocalDetailsUseCase: IGetLocalDetailsUseCase,
        private val getRemoteDetailsUseCase: IGetRemoteDetailsUseCase
) : IGetMovieDetailUseCase, AsyncTasksManager() {

    override suspend fun invoke(data: Int): SResult<MovieEntity> = doAsyncAwait {
        getLocalDetailsUseCase.invoke(data).let {
            if (it is SResult.Success) it
            else getRemoteDetailsUseCase.invoke(data)
        }
    }
}

interface IGetMovieDetailUseCase : IUseCase.InOut<Int, SResult<MovieEntity>>