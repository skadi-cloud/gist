package cloud.skadi.gist.storage

import cloud.skadi.gist.data.Gist
import cloud.skadi.gist.data.GistRoot
import cloud.skadi.gist.shared.GistVisibility
import io.ktor.application.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.ObjectCannedACL
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest
import java.io.InputStream
import java.time.Duration


class S3BasedStorage(private val s3: S3Client, private val presigner: S3Presigner, private val bucketName: String) :
    StorageProvider {

    private fun GistRoot.originalKey() = "${this.gist.id.value}/${this.id.value}/original.png"
    private fun Gist.previewKey() = "${this.id.value}/preview.png"

    override fun getUrls(call: ApplicationCall, root: GistRoot): UrlList {
        val key = root.originalKey()
        return getUrlsInternal(key, root.gist.visibility)
    }

    override fun getPreviewUrl(call: ApplicationCall, gist: Gist): String {
        val key = gist.previewKey()
        return getUrlsInternal(key, gist.visibility).mainUrl
    }

    private fun getUrlsInternal(key: String, visibility: GistVisibility): UrlList {
        return if (visibility != GistVisibility.Public) {
            val getObjectRequest = GetObjectRequest.builder().bucket(bucketName).key(key).build()

            val getObjectPresignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10))
                .getObjectRequest(getObjectRequest)
                .build()

            val presignedGetObjectRequest: PresignedGetObjectRequest =
                presigner.presignGetObject(getObjectPresignRequest)
            UrlList(presignedGetObjectRequest.url().toExternalForm(), emptyList())
        } else {
            val publicUrl = s3.utilities().getUrl { builder -> builder.bucket(bucketName).key(key) }.toExternalForm()
            UrlList(publicUrl, emptyList())
        }
    }

    override suspend fun storeRoot(root: GistRoot, input: InputStream) {
        val acl = if (root.gist.visibility == GistVisibility.Public) {
            ObjectCannedACL.PUBLIC_READ
        } else {
            ObjectCannedACL.PRIVATE
        }
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(root.originalKey())
            .acl(acl)
            .build()
        val bytes = withContext(Dispatchers.IO) {
            input.readAllBytes()
        }

        s3.putObject(objectRequest, RequestBody.fromBytes(bytes))
    }

    override suspend fun storePreview(gist: Gist, input: InputStream) {
        val acl = if (gist.visibility == GistVisibility.Public) {
            ObjectCannedACL.PUBLIC_READ
        } else {
            ObjectCannedACL.PRIVATE
        }
        val objectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(gist.previewKey())
            .acl(acl)
            .build()
        val bytes = withContext(Dispatchers.IO) {
            input.readAllBytes()
        }

        s3.putObject(objectRequest, RequestBody.fromBytes(bytes))
    }
}