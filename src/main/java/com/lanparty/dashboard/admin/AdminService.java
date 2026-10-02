package com.lanparty.dashboard.admin;

import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lanparty.dashboard.common.BadRequestException;
import com.lanparty.dashboard.common.NotFoundException;

@Service
public class AdminService {

    static final int CODE_LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminRepository admins;
    private final PasswordEncoder encoder;

    public AdminService(AdminRepository admins, PasswordEncoder encoder) {
        this.admins = admins;
        this.encoder = encoder;
    }

    /** Codes are unique per admin, so the code alone identifies who logs in. */
    @Transactional(readOnly = true)
    public Optional<Admin> findByCode(String code) {
        if (code.isBlank()) {
            return Optional.empty();
        }
        return admins.findByEnabledTrue().stream()
                .filter(a -> encoder.matches(code, a.getCodeHash()))
                .findFirst();
    }

    @Transactional(readOnly = true)
    public List<Admin> list() {
        return admins.findAllByOrderByName();
    }

    public record CreatedAdmin(Admin admin, String code) {
    }

    @Transactional
    public CreatedAdmin create(String name) {
        return create(name, generateUniqueCode());
    }

    @Transactional
    public CreatedAdmin create(String name, String code) {
        if (name == null || name.isBlank()) {
            throw new BadRequestException("Name fehlt.");
        }
        if (admins.existsByNameIgnoreCase(name.trim())) {
            throw new BadRequestException("Admin «" + name + "» existiert bereits.");
        }
        Admin admin = admins.save(new Admin(name.trim(), encoder.encode(code), hint(code)));
        return new CreatedAdmin(admin, code);
    }

    @Transactional
    public CreatedAdmin regenerateCode(Long id) {
        Admin admin = admins.findById(id).orElseThrow(() -> new NotFoundException("Admin nicht gefunden."));
        String code = generateUniqueCode();
        admin.changeCode(encoder.encode(code), hint(code));
        return new CreatedAdmin(admin, code);
    }

    @Transactional
    public void delete(Long id, Long currentAdminId) {
        if (id.equals(currentAdminId)) {
            throw new BadRequestException("Du kannst dich nicht selbst entfernen.");
        }
        if (admins.count() <= 1) {
            throw new BadRequestException("Der letzte Admin kann nicht entfernt werden.");
        }
        admins.deleteById(id);
    }

    @Transactional(readOnly = true)
    public boolean none() {
        return admins.count() == 0;
    }

    private String generateUniqueCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder code = new StringBuilder();
            for (int d = 0; d < CODE_LENGTH; d++) {
                code.append(RANDOM.nextInt(10));
            }
            if (findByCode(code.toString()).isEmpty()) {
                return code.toString();
            }
        }
        throw new IllegalStateException("Could not generate a unique admin code");
    }

    private static String hint(String code) {
        return code.length() <= 2 ? code : code.substring(code.length() - 2);
    }
}
