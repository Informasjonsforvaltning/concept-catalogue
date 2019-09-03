package no.begrepskatalog.conceptregistration

import no.begrepskatalog.conceptregistration.storage.SqlStore
import no.begrepskatalog.generated.api.CollectionsApi
import no.begrepskatalog.generated.model.Begrep
import no.begrepskatalog.generated.model.Kildebeskrivelse
import no.begrepskatalog.generated.model.Status
import no.difi.skos_ap_no.concept.builder.Conceptcollection.CollectionBuilder
import no.difi.skos_ap_no.concept.builder.ModelBuilder
import no.difi.skos_ap_no.concept.builder.generic.AudienceType
import no.difi.skos_ap_no.concept.builder.generic.SourceType
import org.apache.jena.rdf.model.Resource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.RestController
import java.io.StringWriter
import javax.servlet.http.HttpServletRequest


@RestController
@CrossOrigin(value = "*")
class HarvestEndpoint(val sqlStore: SqlStore) : CollectionsApi {

    @Value("\${application.baseURL}")
    lateinit var baseURL: String

    private val logger = LoggerFactory.getLogger(HarvestEndpoint::class.java)

    override fun getCollections(httpServletRequest: HttpServletRequest, publisher: String): ResponseEntity<Any> {
        logger.info("Harvest - request")

        val allPublishedBegrepByCompany = sqlStore.getBegrepByCompany(publisher, Status.PUBLISERT)

        logger.info("Harvest found ${allPublishedBegrepByCompany.size} Begrep")

        var collectionBuilder = ModelBuilder.builder()
                                    .collectionBuilder("https://registrering-begrep.ut1.fellesdatakatalog.brreg.no/")
                                        .publisher(publisher)
                                        .name(publisher + " sin samling")

        for (begrep in allPublishedBegrepByCompany) {
            appendBegrepToCollection(begrep, collectionBuilder)
        }

        val writer = StringWriter()
        collectionBuilder.build().build().write(writer, "TURTLE")
        println(writer.buffer)

        return ResponseEntity.ok(writer.buffer.toString())
    }

    fun appendBegrepToCollection(begrep: Begrep, collectionBuilder: CollectionBuilder): Resource {
        val urlForAccessingThisBegrepsRegistration = baseURL + begrep.ansvarligVirksomhet.id + "/" + begrep.id

        var conceptBuilder = collectionBuilder.conceptBuilder(urlForAccessingThisBegrepsRegistration)
        val definitionBuilder = conceptBuilder.definitionBuilder()
        val sourceDescriptionBuilder = definitionBuilder.sourcedescriptionBuilder()
        val sourceBuilder = sourceDescriptionBuilder.sourceBuilder()


        if (begrep.kildebeskrivelse != null) {

            when (begrep.kildebeskrivelse.forholdTilKilde) {
                Kildebeskrivelse.ForholdTilKildeEnum.EGENDEFINERT -> sourceDescriptionBuilder.sourcetype(SourceType.Source.Userdefined)
                Kildebeskrivelse.ForholdTilKildeEnum.BASERTPAAKILDE -> sourceDescriptionBuilder.sourcetype(SourceType.Source.BasedOn)
                Kildebeskrivelse.ForholdTilKildeEnum.SITATFRAKILDE-> sourceDescriptionBuilder.sourcetype(SourceType.Source.QuoteFrom)
            }
            begrep.kildebeskrivelse.kilde?.forEach{
                sourceBuilder.label(it.tekst,"nb")
                        .seeAlso(it.uri)
            }

            sourceBuilder.build()
            sourceDescriptionBuilder.build()
        }

        definitionBuilder
            .text(begrep.definisjon, "nb")
            .audience(AudienceType.Audience.Public)
            .scopeNote(begrep.merknad ?: "", "nb")
            .scopeBuilder()
                .label(begrep.omfang?.tekst ?: "", "nb")
                .seeAlso(begrep.omfang?.uri)
            .build()
            .modified(begrep.gyldigFom)
        .build()

        conceptBuilder
            .identifier(begrep.id)
            .publisher(begrep.ansvarligVirksomhet.id)
            .prefLabelBuilder()
                .label(begrep.anbefaltTerm ?: "", "no")
            .build()
            .example(begrep.eksempel, "nb")
            .subject(begrep.fagområde, "nb")
            .contactPointBuilder()
                .email(begrep.kontaktpunkt?.harEpost ?: "")
                .telephone(begrep.kontaktpunkt?.harTelefon ?: "")
            .build()


        begrep.bruksområde.forEach { bruksområde -> conceptBuilder = conceptBuilder.domainOfUse(bruksområde, "nb") }

        var altLabelBuilder = conceptBuilder.altLabelBuilder()
        begrep.tillattTerm.forEach { tillattTerm -> altLabelBuilder = altLabelBuilder.label(tillattTerm, "nb") }
        conceptBuilder = altLabelBuilder.build()

        var hiddenLabelBuilder = conceptBuilder.hiddenLabelBuilder()
        begrep.frarådetTerm.forEach { frarådetTerm -> hiddenLabelBuilder = hiddenLabelBuilder.label(frarådetTerm, "nb") }
        conceptBuilder = hiddenLabelBuilder.build()


        return conceptBuilder.build().resource
    }
}