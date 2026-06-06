package com.fee.app.schoolfeeapp.common.events;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

@Builder
@Data
public class ParentInvitationEvent {
    private  UUID guardianId;
    private UUID userId;
}
