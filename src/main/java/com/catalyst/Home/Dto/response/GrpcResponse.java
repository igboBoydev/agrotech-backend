package com.catalyst.Home.Dto.response;

import io.grpc.Status;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GrpcResponse<T> {
    private String message;
    private String code;
    private T data;
    private Status.Code status;
}
