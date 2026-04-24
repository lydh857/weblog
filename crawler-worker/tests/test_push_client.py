from app.services.push_client import _format_ingest_rejected_reason


def test_format_ingest_rejected_reason_duplicate_content() -> None:
    message = _format_ingest_rejected_reason(
        {
            "reasonCode": "duplicate_content",
            "reasonMessage": "contentFingerprint 已存在",
        }
    )

    assert message == "候选内容入库被拒绝：内容指纹已存在，系统判定为重复内容（contentFingerprint 已存在）"


def test_format_ingest_rejected_reason_with_duplicate_context() -> None:
    message = _format_ingest_rejected_reason(
        {
            "reasonCode": "duplicate_content",
            "reasonMessage": "contentFingerprint 已存在",
            "duplicateCandidateId": 88,
            "duplicateTitle": "旧文章标题",
            "duplicateExternalUrl": "https://example.com/old",
        }
    )

    assert message == "候选内容入库被拒绝：内容指纹已存在，系统判定为重复内容（contentFingerprint 已存在）；已命中重复内容：候选ID 88 / 标题《旧文章标题》 / https://example.com/old"


def test_format_ingest_rejected_reason_unknown() -> None:
    message = _format_ingest_rejected_reason({"reasonCode": "unknown"})

    assert message == "候选内容入库被拒绝：后端拒绝接收入库"
