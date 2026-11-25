"""Wire contracts observed with the Spring security chain and explicit CORS origin."""

ORIGIN = "http://localhost:13000"
METHODS = "GET,POST,PUT,DELETE,OPTIONS"
AUTH = 'Bearer error="invalid_token", error_description="invalid_token"'
INVALID_TOKEN = {"error": "invalid_token", "error_description": "refreshToken 无效", "needRefresh": False}
POST_ERROR = {"code": 9999, "message": "系统异常，请稍后再试", "data": None}
SECURITY = {"X-Content-Type-Options": "nosniff", "X-XSS-Protection": "0", "X-Frame-Options": "DENY",
            "Vary": "Origin, Access-Control-Request-Method, Access-Control-Request-Headers"}
LANG = {"Content-Language": "zh-CN"}
JSON_HEADERS = {**LANG, "Content-Type": "application/json"}
INFO = object()
SCENARIOS = [
    ("anonymous", "GET", {}, 200, JSON_HEADERS, INFO),
    ("head", "HEAD", {}, 200, JSON_HEADERS, b""),
    ("options", "OPTIONS", {}, 200, {**LANG, "Allow": "GET,HEAD,OPTIONS", "Accept-Patch": ""}, b""),
    ("empty POST", "POST", {}, 200, JSON_HEADERS, POST_ERROR),
    ("json", "GET", {"Accept": "application/json"}, 200, JSON_HEADERS, INFO),
    ("plain", "GET", {"Accept": "text/plain"}, 406,
     {**LANG, "Accept": "application/json, application/*+json, application/yaml"}, b""),
    ("malformed bearer", "GET", {"Authorization": "Bearer malformed"}, 401,
     {**LANG, "WWW-Authenticate": AUTH, "Content-Type": "application/json;charset=UTF-8"}, INVALID_TOKEN),
    ("allowed preflight", "OPTIONS", {"Origin": ORIGIN, "Access-Control-Request-Method": "GET"}, 200,
     {"Access-Control-Allow-Origin": ORIGIN, "Access-Control-Allow-Methods": METHODS,
      "Access-Control-Allow-Credentials": "true"}, b""),
    ("denied preflight", "OPTIONS", {"Origin": "https://denied.invalid", "Access-Control-Request-Method": "GET"},
     403, {}, b"Invalid CORS request"),
]
HEADER_NAMES = set(SECURITY).union(*(s[4] for s in SCENARIOS),
                                   ("Cache-Control", "Pragma", "Expires", "Access-Control-Allow-Headers",
                                    "Access-Control-Max-Age", "Access-Control-Expose-Headers"))


def normalize(name, value):
    if value is not None and name in {"Vary", "Allow", "Access-Control-Allow-Methods", "Accept"}:
        return {part.strip() for part in value.split(",")}
    return value
