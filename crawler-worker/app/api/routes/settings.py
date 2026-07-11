from fastapi import APIRouter

from app.schemas import PushTargetConfigRequest, PushTargetConfigResponse, PushTargetProfile
from app.services.push_target import push_target_manager


router = APIRouter(prefix="/settings", tags=["settings"])


@router.get("/push-target", response_model=PushTargetConfigResponse)
async def get_push_target() -> PushTargetConfigResponse:
    config = push_target_manager.load_config()
    return PushTargetConfigResponse(
        active_target=config["active_target"],
        profiles={key: PushTargetProfile(**value) for key, value in config["profiles"].items()},
    )


@router.put("/push-target", response_model=PushTargetConfigResponse)
async def update_push_target(payload: PushTargetConfigRequest) -> PushTargetConfigResponse:
    config = push_target_manager.update_config(payload.active_target, {key: value.model_dump() for key, value in payload.profiles.items()})
    return PushTargetConfigResponse(
        active_target=config["active_target"],
        profiles={key: PushTargetProfile(**value) for key, value in config["profiles"].items()},
    )
