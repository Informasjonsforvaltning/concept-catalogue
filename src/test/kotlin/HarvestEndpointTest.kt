package no.brreg.conceptcatalogue

import com.nhaarman.mockitokotlin2.mock
import no.begrepskatalog.generated.model.Virksomhet
import org.junit.Assert.assertNotNull
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import javax.servlet.http.HttpServletRequest

@RunWith(SpringRunner::class)
@SpringBootTest
class HarvestEndpointTest {

    @Autowired
    lateinit var harvestEndpoint: HarvestEndpoint

    fun createTestVirksomhet(): no.begrepskatalog.generated.model.Virksomhet {

        val testVirksomhet = Virksomhet().apply {
            id = "910244132"
            navn = "Ramsund og Rognand revisjon"
            orgPath = "/helt/feil/dummy/path"
            prefLabel = "preflabel"
            uri = "ramsumdURI"
        }
        return testVirksomhet
    }

    @Ignore
    @Test
    fun testHarvesting() {
        val httpServletRequestMock: HttpServletRequest = mock()
        val someResponse = harvestEndpoint.getCollections(httpServletRequestMock, "910244132")
        assertNotNull(someResponse)
    }

    @Ignore
    @Test
    fun contextLoads() {
    }
}