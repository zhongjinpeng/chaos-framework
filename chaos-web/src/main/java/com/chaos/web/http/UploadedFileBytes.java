package com.chaos.web.http;

import com.chaos.core.exception.BizException;
import com.chaos.core.exception.ErrorCode;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 上传文件读取：把 {@link MultipartFile#getBytes()} 的 IO 失败统一转换为业务异常。
 *
 * <p>文件大小、空文件和业务格式校验仍由调用方或应用服务负责，本工具只处理读取动作。
 */
public final class UploadedFileBytes {

  private UploadedFileBytes() {}

  /**
   * 读取上传文件字节。
   *
   * @param file 已通过请求校验的上传文件
   * @param errorCode 读取失败时使用的业务错误码
   * @param message 读取失败时覆盖错误码默认提示的说明
   * @return 文件字节
   * @throws BizException 文件读取失败时抛出，携带调用方指定的错误码
   */
  public static byte[] read(MultipartFile file, ErrorCode errorCode, String message) {
    try {
      return file.getBytes();
    } catch (IOException ex) {
      throw new BizException(errorCode, message);
    }
  }
}
