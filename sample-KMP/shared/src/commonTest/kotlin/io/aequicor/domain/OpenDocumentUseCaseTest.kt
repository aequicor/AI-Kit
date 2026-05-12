package io.aequicor.domain

import io.aequicor.domain.usecase.OpenDocumentUseCase
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OpenDocumentUseCaseTest {

    @Test
    fun invoke_delegatesToPort_returnsDocument() = runTest {
        val port = FakePdfRenderPort(pageCount = 5)
        val useCase = OpenDocumentUseCase(port)

        val doc = useCase(ByteArray(0))

        assertEquals(5, doc.pageCount)
        assertEquals(5, doc.pages.size)
    }

    @Test
    fun invoke_pagesHaveCorrectIndices() = runTest {
        val port = FakePdfRenderPort(pageCount = 3)
        val useCase = OpenDocumentUseCase(port)

        val doc = useCase(ByteArray(0))

        doc.pages.forEachIndexed { i, page -> assertEquals(i, page.index) }
    }
}
