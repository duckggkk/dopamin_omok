package com.dopamin.omok.shop.domain;

/**
 * 보호 에셋(이미지/오디오) 응답 타입.
 * - Data: classpath/로컬 파일 바이너리
 * - SignedUrl: S3 등 외부 스토리지의 서명된 임시 URL
 */
public sealed interface AssetResult permits AssetResult.Data, AssetResult.SignedUrl {

    record Data(byte[] bytes, String contentType) implements AssetResult {}

    record SignedUrl(String url) implements AssetResult {}
}
