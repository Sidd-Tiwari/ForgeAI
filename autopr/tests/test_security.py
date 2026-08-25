import hashlib, hmac
from forge_autopr.security import verify_signature

def test_signature_verification():
    body=b'{"safe":true}'; secret="secret"; signature="sha256="+hmac.new(secret.encode(),body,hashlib.sha256).hexdigest()
    assert verify_signature(secret,body,signature)
    assert not verify_signature(secret,body,signature[:-1]+"0")
