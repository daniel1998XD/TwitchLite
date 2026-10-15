package com.example.twitchlite.data.remote.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.example.twitchlite.data.local.TokenManager
import com.example.twitchlite.data.remote.api.TwitchHelixApi
import com.example.twitchlite.data.remote.dto.HelixStreamDto

class FollowedStreamsPagingSource(
    private val helixApi: TwitchHelixApi,
    private val tokenManager: TokenManager,
    private val clientId: String
) : PagingSource<String, HelixStreamDto>() {

    override suspend fun load(params: LoadParams<String>): LoadResult<String, HelixStreamDto> {
        return try {
            val token = tokenManager.getAccessToken()
                ?: return LoadResult.Error(IllegalStateException("Configure seu Token OAuth para listar os canais seguidos."))
            val userId = tokenManager.getUserId()
                ?: return LoadResult.Error(IllegalStateException("User ID não encontrado."))

            val response = helixApi.getFollowedStreams(
                bearerToken = "Bearer $token",
                clientId = clientId,
                userId = userId,
                pageSize = params.loadSize.coerceAtMost(100),
                afterCursor = params.key
            )

            val nextCursor = response.pagination?.cursor

            LoadResult.Page(
                data = response.streams,
                prevKey = null,
                nextKey = if (response.streams.isEmpty()) null else nextCursor
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    override fun getRefreshKey(state: PagingState<String, HelixStreamDto>): String? = null
}