package auth

import (
	"strings"
	"testing"
)

func TestSpringPasswordVectors(t *testing.T) {
	raw := "向量🙂"
	const javaHash = "$2a$10$abcdefghijklmnopqrstuuiN8IvSGb8OzxGpzQWehP3/O4ZRnSYAm"
	const emptyHash = "$2a$10$abcdefghijklmnopqrstuujr5dF95nlffmIUNubpV71FPx1zncrEm"
	const longHash = "$2a$10$abcdefghijklmnopqrstuuhBb/0dYbuuS1CS8hp30IXhlFBW2VAWG"
	const original = "$2$10$abcdefghijklmnopqrstuuj7kztwSAR7M4lGkVFfn0mcGMQiHDc7q"
	for _, codec := range []PasswordCodec{{Encoder: "unknown"}, {Encoder: "SHA256", Pepper: "盐"}} {
		for _, version := range []string{"2a", "2b", "2y"} {
			hash := strings.Replace(javaHash, "2a", version, 1)
			if !codec.Matches(raw, hash) || codec.Matches("wrong", hash) {
				t.Errorf("bcrypt %s, encoder %q", version, codec.Encoder)
			}
		}
		if !codec.Matches("", emptyHash) || !codec.Matches(strings.Repeat("x", 72)+"y", longHash) {
			t.Error("empty or 72 byte verification")
		}
		for _, bad := range []string{
			"", javaHash + "x", javaHash[:59],
			strings.Replace(javaHash, "2a", "2x", 1),
			strings.Replace(javaHash, "10", "03", 1),
			javaHash[:28] + "v" + javaHash[29:], // Non-canonical salt padding.
		} {
			if codec.Matches(raw, bad) {
				t.Errorf("accepted malformed %q", bad)
			}
		}
	}
	var bc PasswordCodec
	if !bc.Matches(raw, original) || (PasswordCodec{Encoder: "sha256"}).Matches(raw, original) {
		t.Error("legacy version routing")
	}
	for _, pw := range []string{"", strings.Repeat("界", 24)} {
		h, err := bc.Encode(pw)
		if err != nil || !strings.HasPrefix(h, "$2a$10$") || !bc.Matches(pw, h) {
			t.Errorf("encode length %d: %v", len(pw), err)
		}
	}
	if _, err := bc.Encode(strings.Repeat("界", 24) + "x"); err == nil {
		t.Error("encoded 73 bytes")
	}
	for _, tc := range []struct{ output, want string }{
		{"hex", "05d0daa261d005bc45d80645b30f5ce463331ef6348ea22f516368dd848cc1b6"},
		{"BaSe64", "BdDaomHQBbxF2AZFsw9c5GMzHvY0jqIvUWNo3YSMwbY="},
	} {
		c := PasswordCodec{Encoder: "sHa256", Pepper: "盐", Output: tc.output}
		h, err := c.Encode(raw)
		if err != nil || h != tc.want || !c.Matches(raw, tc.want) || c.Matches("wrong", tc.want) || bc.Matches(raw, h) {
			t.Errorf("SHA %s: %q, %v", tc.output, h, err)
		}
	}
}
