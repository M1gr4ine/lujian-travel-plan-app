import CoreFoundation
import Foundation

enum HTMLImportError: LocalizedError, Equatable {
    case fileTooLarge
    case unsupportedType
    case invalidHTML
    case incompatibleContract(String)
    case unreadableFile

    var errorDescription: String? {
        switch self {
        case .fileTooLarge: "HTML 文件不能超过 50 MB"
        case .unsupportedType: "仅支持 .html 或 .htm 文件"
        case .invalidHTML: "文件不是可识别的 HTML"
        case let .incompatibleContract(reason): "旅笺数据契约不兼容：\(reason)"
        case .unreadableFile: "无法读取所选文件"
        }
    }
}

struct DecodedHTML {
    let text: String
    let data: Data
    let fileName: String
    let encodingName: String
}

enum HTMLDecoder {
    static let maximumBytes = 50 * 1024 * 1024
    static let gb18030Encoding = String.Encoding(
        rawValue: CFStringConvertEncodingToNSStringEncoding(
            CFStringEncoding(kCFStringEncodingGB_18030_2000)
        )
    )

    static func decode(data: Data, fileName: String) throws -> DecodedHTML {
        guard data.count <= maximumBytes else { throw HTMLImportError.fileTooLarge }
        let fileExtension = (fileName as NSString).pathExtension.lowercased()
        guard ["html", "htm"].contains(fileExtension) else { throw HTMLImportError.unsupportedType }

        let decoded: (String, String)?
        if data.starts(with: [0xEF, 0xBB, 0xBF]) {
            decoded = String(data: data.dropFirst(3), encoding: .utf8).map { ($0, "UTF-8 BOM") }
        } else if data.starts(with: [0xFF, 0xFE]) {
            decoded = String(data: data.dropFirst(2), encoding: .utf16LittleEndian).map { ($0, "UTF-16LE BOM") }
        } else if data.starts(with: [0xFE, 0xFF]) {
            decoded = String(data: data.dropFirst(2), encoding: .utf16BigEndian).map { ($0, "UTF-16BE BOM") }
        } else if let declared = declaredEncoding(in: data),
                  let text = String(data: data, encoding: declared.encoding) {
            decoded = (text, declared.name)
        } else if let text = String(data: data, encoding: .utf8) {
            decoded = (text, "UTF-8")
        } else if let text = String(data: data, encoding: gb18030Encoding) {
            decoded = (text, "GB18030")
        } else {
            decoded = nil
        }

        guard let decoded, looksLikeHTML(decoded.0) else { throw HTMLImportError.invalidHTML }
        return DecodedHTML(text: decoded.0, data: data, fileName: fileName, encodingName: decoded.1)
    }

    private static func declaredEncoding(in data: Data) -> (encoding: String.Encoding, name: String)? {
        let header = String(data: data.prefix(8_192), encoding: .isoLatin1) ?? ""
        let pattern = #"(?i)charset\s*=\s*[\"']?\s*([a-z0-9._-]+)"#
        guard let regex = try? NSRegularExpression(pattern: pattern),
              let match = regex.firstMatch(in: header, range: NSRange(header.startIndex..., in: header)),
              let range = Range(match.range(at: 1), in: header) else { return nil }
        switch header[range].lowercased() {
        case "utf-8", "utf8": return (.utf8, "UTF-8")
        case "utf-16", "utf16": return (.utf16, "UTF-16")
        case "utf-16le": return (.utf16LittleEndian, "UTF-16LE")
        case "utf-16be": return (.utf16BigEndian, "UTF-16BE")
        case "gb18030", "gbk", "gb2312": return (gb18030Encoding, "GB18030")
        default: return nil
        }
    }

    private static func looksLikeHTML(_ text: String) -> Bool {
        let prefix = String(text.prefix(65_536))
        return prefix.range(
            of: #"(?is)<\s*(?:!doctype\s+html|html\b|head\b|body\b|title\b|meta\b|script\b)"#,
            options: .regularExpression
        ) != nil
    }
}
