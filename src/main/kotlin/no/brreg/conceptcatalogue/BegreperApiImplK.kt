package no.brreg.conceptcatalogue

import io.swagger.annotations.ApiParam
import no.begrepskatalog.generated.api.BegreperApi
import no.begrepskatalog.generated.model.Begrep
import no.begrepskatalog.generated.model.Endringslogelement
import no.begrepskatalog.generated.model.JsonPatchOperation
import no.begrepskatalog.generated.model.Status
import no.brreg.conceptcatalogue.repository.BegrepRepository
import no.brreg.conceptcatalogue.security.FdkPermissions
import no.brreg.conceptcatalogue.utils.patchBegrep
import no.brreg.conceptcatalogue.validation.isValidBegrep
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RestController
import java.time.OffsetDateTime
import java.util.*
import javax.json.JsonException
import javax.servlet.http.HttpServletRequest
import javax.validation.Valid

private val logger = LoggerFactory.getLogger(BegreperApiImplK::class.java)

@RestController
class BegreperApiImplK(val begrepRepository: BegrepRepository, val fdkPermissions: FdkPermissions) : BegreperApi {

    @Value("\${application.baseURL}")
    lateinit var baseURL: String

    override fun getBegrep(httpServletRequest: HttpServletRequest?, @PathVariable orgnumber: String?, status: Status?): ResponseEntity<List<Begrep>> {
        logger.info("Get begrep $orgnumber")
        //todo generate accessible organisation list filter
        //todo generate status filter or remove from spec

        if (orgnumber != null && fdkPermissions.hasPermission(orgnumber, "publisher", "admin")) {
            return ResponseEntity.ok(begrepRepository.getBegrepByAnsvarligVirksomhetId(orgnumber))
        }
        return ResponseEntity.ok(mutableListOf())
    }

    @GetMapping("/ping")
    fun ping(): ResponseEntity<Void> =
            ResponseEntity.ok().build()

    @GetMapping("/ready")
    fun ready(): ResponseEntity<Void> {
        try {
            begrepRepository.count()
            return ResponseEntity.ok().build()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
    }

    override fun createBegrep(httpServletRequest: HttpServletRequest, @ApiParam(value = "", required = true) @Valid @RequestBody begrep: Begrep): ResponseEntity<Void> {

        if (!fdkPermissions.hasPermission(begrep?.ansvarligVirksomhet?.id, "publisher", "admin")) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }

        var newBegrep: Begrep = begrep;
        newBegrep.id = UUID.randomUUID().toString()

        newBegrep.updateLastChangedAndByWhom()

        begrepRepository.insert(newBegrep);

        val headers = HttpHeaders()
        //todo there must be a better way to build path
        headers.add(HttpHeaders.LOCATION, httpServletRequest.requestURI.removeSuffix("/") + "/" + newBegrep.id)
        headers.add(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.LOCATION)
        return ResponseEntity<Void>(headers, HttpStatus.CREATED)
    }

    override fun setBegrepById(httpServletRequest: HttpServletRequest?, id: String?, jsonPatchOperations: List<JsonPatchOperation>?, validate: Boolean?): ResponseEntity<Begrep> {
        if (id == null) {
            throw RuntimeException("Attempt to PATCH begrep with no id path variable given")
        }

        if (jsonPatchOperations == null) {
            throw RuntimeException("Attempt to PATCH begrep with no changes provided. Id provided was $id")
        }

        //Get the begrep, and just update
        val storedBegrep = begrepRepository.getBegrepById(id)
                ?: throw RuntimeException("Attempt to PATCH begrep that does not already exist. Begrep id $id")

        if (!fdkPermissions.hasPermission(storedBegrep.ansvarligVirksomhet?.id, "publisher", "admin")) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }

        try {
            val patchedBegrep = patchBegrep(storedBegrep, jsonPatchOperations)

            //override any updates on sensitive fields
            patchedBegrep.id = storedBegrep.id
            patchedBegrep.ansvarligVirksomhet = storedBegrep.ansvarligVirksomhet

            patchedBegrep.updateLastChangedAndByWhom()

            if (patchedBegrep.status != Status.UTKAST && !isValidBegrep(patchedBegrep)) {
                logger.info("Begrep $patchedBegrep.id has not passed validation for non draft begrep and has not been saved ")

                return ResponseEntity(HttpStatus.BAD_REQUEST)
            }

            begrepRepository.save(patchedBegrep)

            return ResponseEntity.ok(patchedBegrep)

        } catch (exception: Exception) {
            when (exception) {
                is JsonException, is IllegalArgumentException -> throw RuntimeException("Invalid patch operation. Begrep id $id")
                else -> throw exception
            }
        }

    }

    override fun getBegrepById(httpServletRequest: HttpServletRequest, @ApiParam(value = "id", required = true) @PathVariable("id") id: String): ResponseEntity<Begrep> {

        val begrep = begrepRepository.getBegrepById(id)

        if (!fdkPermissions.hasPermission(begrep?.ansvarligVirksomhet?.id, "publisher", "admin")) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }

        return begrep?.let { ResponseEntity.ok(it) } ?: ResponseEntity(HttpStatus.NOT_FOUND)
    }

    private fun Begrep.updateLastChangedAndByWhom() {
        if (endringslogelement == null) {
            endringslogelement = Endringslogelement()
        }
        endringslogelement.apply {
            endringstidspunkt = OffsetDateTime.now()
            brukerId = "todo" //TODO: When auth is ready read username from auth context
        }
    }

    override fun deleteBegrepById(httpServletRequest: HttpServletRequest, @ApiParam(value = "id", required = true) @PathVariable("id") id: String): ResponseEntity<Void> {

        val begrep = begrepRepository.getBegrepById(id)

        //Validate that begrep exists
        if (begrep == null) {
            logger.info("Request to delete non-existing begrep, id $id ignored")
            return ResponseEntity(HttpStatus.NOT_FOUND)
        }

        if (!fdkPermissions.hasPermission(begrep.ansvarligVirksomhet?.id, "publisher", "admin")) {
            return ResponseEntity(HttpStatus.FORBIDDEN)
        }

        //Validate that begrep is NOT published
        if (begrep?.status == Status.PUBLISERT) {
            logger.warn("Attempt to delete PUBLISHED begrep $id ignored")
            return ResponseEntity(HttpStatus.BAD_REQUEST)
        }

        logger.info("Deleting begrep id $id organisation ${begrep?.ansvarligVirksomhet?.id}")
        begrepRepository.removeBegrepById(begrep.id)

        return ResponseEntity(HttpStatus.OK)
    }
}