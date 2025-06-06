package com.example.aicoserver.user.controller;

import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.CreatePreauthenticatedRequestRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.requests.DeleteObjectRequest;
import com.oracle.bmc.objectstorage.model.CreatePreauthenticatedRequestDetails;
import com.oracle.bmc.objectstorage.model.PreauthenticatedRequest;
import com.oracle.bmc.objectstorage.responses.CreatePreauthenticatedRequestResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.time.ZonedDateTime;
import java.util.Date;

@RestController
@RequestMapping("/api/images")
public class ImageController {

    @Autowired
    private ObjectStorageClient objectStorageClient;

    @Value("${spring.cloud.oci.bucket}")
    private String bucketName;

    @Value("${spring.cloud.oci.namespace}")
    private String namespaceName;

    @Value("${spring.cloud.oci.region}")
    private String region;

    @Value("${spring.cloud.oci.bucket.public:false}")
    private boolean isPublicBucket;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadImage(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("업로드할 파일이 없습니다.");
        }

        String objectName = "opc/image/IMG_" + System.currentTimeMillis() + ".jpg";

        try (InputStream inputStream = file.getInputStream()) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .contentType(file.getContentType())
                    .putObjectBody(inputStream)
                    .contentLength(file.getSize())
                    .build();

            PutObjectResponse response = objectStorageClient.putObject(putRequest);

            String imageUrl = isPublicBucket
                    ? String.format("https://objectstorage.%s.oraclecloud.com/n/%s/b/%s/o/%s",
                    region, namespaceName, bucketName, objectName)
                    : generatePreAuthUrl(objectName);

            return ResponseEntity.ok(imageUrl);

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("업로드 실패: " + e.getMessage());
        }
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteImage(@RequestParam(required = false) String imageUrl) {
        if (imageUrl == null || imageUrl.isEmpty()) {
            return ResponseEntity.badRequest().body("imageUrl 파라미터 필수");
        }

        String[] parts = imageUrl.split("/o/");
        String objectName = (parts.length >= 2) ? parts[1] : null;

        if (objectName == null || objectName.isEmpty()) {
            return ResponseEntity.badRequest().body("유효하지 않은 imageUrl 형식");
        }

        try {
            DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                    .namespaceName(namespaceName)
                    .bucketName(bucketName)
                    .objectName(objectName)
                    .build();

            objectStorageClient.deleteObject(deleteRequest);
            return ResponseEntity.ok("이미지 삭제 성공");

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("삭제 실패: " + e.getMessage());
        }
    }

    private String generatePreAuthUrl(String objectName) {
        CreatePreauthenticatedRequestDetails details = CreatePreauthenticatedRequestDetails.builder()
                .name("preauth-" + System.currentTimeMillis())
                .objectName(objectName)
                .accessType(CreatePreauthenticatedRequestDetails.AccessType.ObjectRead)
                .timeExpires(Date.from(ZonedDateTime.now().plusHours(1).toInstant()))
                .build();

        CreatePreauthenticatedRequestRequest request = CreatePreauthenticatedRequestRequest.builder()
                .namespaceName(namespaceName)
                .bucketName(bucketName)
                .createPreauthenticatedRequestDetails(details)
                .build();

        CreatePreauthenticatedRequestResponse response = objectStorageClient.createPreauthenticatedRequest(request);
        PreauthenticatedRequest par = response.getPreauthenticatedRequest();

        return "https://objectstorage." + region + ".oraclecloud.com" + par.getAccessUri();
    }
}
