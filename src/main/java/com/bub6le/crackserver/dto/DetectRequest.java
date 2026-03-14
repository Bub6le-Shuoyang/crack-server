package com.bub6le.crackserver.dto;

import lombok.Data;
import java.util.List;

@Data
public class DetectRequest {
    private List<Long> imageIds;
}
