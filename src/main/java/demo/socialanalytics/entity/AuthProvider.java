package demo.socialanalytics.entity;

// Nguồn xác thực của tài khoản. LOCAL dành cho admin tạo sẵn; FACEBOOK/TWITTER
// dùng khi bật Social Login ở bước sau.
public enum AuthProvider {
    LOCAL,
    FACEBOOK,
    TWITTER
}
