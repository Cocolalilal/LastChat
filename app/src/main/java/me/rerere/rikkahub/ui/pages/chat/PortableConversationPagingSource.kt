package me.rerere.rikkahub.ui.pages.chat

import androidx.paging.PagingSource
import androidx.paging.PagingState
import me.rerere.ai.generation.PortableConversationPage
import me.rerere.rikkahub.data.datastore.toConversation
import me.rerere.rikkahub.data.model.Conversation

internal class PortableConversationPagingSource(
    private val loadPage: suspend (offset: Int, loadSize: Int) -> PortableConversationPage,
) : PagingSource<Int, Conversation>() {
    override fun getRefreshKey(state: PagingState<Int, Conversation>): Int? {
        val anchor = state.anchorPosition ?: return null
        val page = state.closestPageToPosition(anchor) ?: return null
        return page.prevKey?.plus(page.data.size) ?: page.nextKey?.minus(page.data.size)?.coerceAtLeast(0)
    }

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, Conversation> {
        return runCatching {
            val offset = params.key ?: 0
            val page = loadPage(offset, params.loadSize)
            LoadResult.Page(
                data = page.items.map { it.toConversation() },
                prevKey = if (offset == 0) null else (offset - params.loadSize).coerceAtLeast(0),
                nextKey = page.nextOffset,
            )
        }.getOrElse { error ->
            LoadResult.Error(error)
        }
    }
}
