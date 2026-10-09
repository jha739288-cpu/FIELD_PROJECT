package com.labmarket.seed;

import com.labmarket.entity.Equipment;
import com.labmarket.entity.EquipmentCondition;
import com.labmarket.entity.EquipmentStatus;
import com.labmarket.entity.MaintenanceStatus;
import com.labmarket.entity.Role;
import com.labmarket.entity.User;
import com.labmarket.repository.EquipmentRepository;
import com.labmarket.repository.RoleRepository;
import com.labmarket.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional demo dataset: a sample VENDOR plus ten Mumbai-laboratory instruments
 * owned by that vendor. Runs ONLY when {@code app.seed.demo-enabled=true}
 * (env {@code APP_SEED_DEMO}). Idempotent: existing codes/usernames are skipped.
 * Synthetic data only — never real people or prices.
 */
@Component
@Order(20)
public class DemoSeedRunner implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(DemoSeedRunner.class);

  private record DemoItem(
      String code,
      String name,
      String category,
      String manufacturer,
      String model,
      String specs,
      String price) {}

  private static final List<DemoItem> ITEMS =
      List.of(
          new DemoItem("DEMO-MIC-01", "Digital Microscope", "Microscope", "OptiLab", "DM-4000",
              "40x–1000x, 5MP camera, LED illumination", "12.50"),
          new DemoItem("DEMO-SPEC-01", "UV-Visible Spectrophotometer", "Spectrophotometer",
              "SpecTron", "UVS-2900", "190–1100 nm, 1 nm bandwidth", "18.00"),
          new DemoItem("DEMO-CEN-01", "Laboratory Centrifuge", "Centrifuge", "SpinMax",
              "LC-15000", "15000 rpm, 24-place rotor", "15.00"),
          new DemoItem("DEMO-PCR-01", "PCR Thermal Cycler", "PCR Machine", "GeneAmp",
              "TC-96", "96-well, gradient block", "22.00"),
          new DemoItem("DEMO-AUT-01", "Autoclave", "Autoclave", "SteriLab", "AV-50",
              "50 L, 121 C gravity cycle", "10.00"),
          new DemoItem("DEMO-INC-01", "Laboratory Incubator", "Incubator", "CultiTemp",
              "IN-180", "180 L, ambient+5 to 70 C", "9.50"),
          new DemoItem("DEMO-BAL-01", "Analytical Balance", "Analytical Balance", "PreciseWeigh",
              "AB-220", "220 g x 0.1 mg, internal calibration", "8.00"),
          new DemoItem("DEMO-PHM-01", "Laboratory pH Meter", "pH Meter", "IonSense",
              "PH-700", "0–14 pH, ATC probe included", "5.00"),
          new DemoItem("DEMO-STI-01", "Magnetic Stirrer", "Stirrer", "MixLab", "MS-5L",
              "5 L, 100–1500 rpm, hotplate", "6.50"),
          new DemoItem("DEMO-SHK-01", "Laboratory Shaker", "Shaker", "OrbiMix",
              "OS-30", "Orbital, 30–300 rpm", "7.50"));

  private final UserRepository users;
  private final RoleRepository roles;
  private final EquipmentRepository equipment;
  private final PasswordEncoder encoder;
  private final boolean demoEnabled;
  private final String vendorUsername;
  private final String vendorPassword;

  public DemoSeedRunner(
      UserRepository users,
      RoleRepository roles,
      EquipmentRepository equipment,
      PasswordEncoder encoder,
      @Value("${app.seed.demo-enabled:false}") boolean demoEnabled,
      @Value("${app.seed.demo-vendor-username:demo-vendor}") String vendorUsername,
      @Value("${app.seed.demo-vendor-password:Demo1234!}") String vendorPassword) {
    this.users = users;
    this.roles = roles;
    this.equipment = equipment;
    this.encoder = encoder;
    this.demoEnabled = demoEnabled;
    this.vendorUsername = vendorUsername;
    this.vendorPassword = vendorPassword;
  }

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    if (!demoEnabled) {
      return;
    }
    log.warn("Demo seed ENABLED — synthetic data only, never for production use");
    User vendor =
        users
            .findByUsername(vendorUsername)
            .orElseGet(
                () -> {
                  Role role =
                      roles
                          .findByName("VENDOR")
                          .orElseThrow(
                              () -> new IllegalStateException("VENDOR role is not seeded"));
                  User u = new User();
                  u.setUsername(vendorUsername);
                  u.setEmail(vendorUsername + "@example.com");
                  u.setPasswordHash(encoder.encode(vendorPassword));
                  u.setFullName("Sample Vendor");
                  u.setEnabled(true);
                  u.getRoles().add(role);
                  return users.save(u);
                });
    int added = 0;
    for (DemoItem item : ITEMS) {
      if (equipment.existsByEquipmentCode(item.code())) {
        continue;
      }
      Equipment e = new Equipment();
      e.setEquipmentCode(item.code());
      e.setName(item.name());
      e.setCategory(item.category());
      e.setDescription("Demo instrument for evaluation runs");
      e.setManufacturer(item.manufacturer());
      e.setModel(item.model());
      e.setLaboratory("Mumbai Laboratory");
      e.setSpecifications(item.specs());
      e.setPricePerHour(new BigDecimal(item.price()));
      e.setQuantity(1);
      e.setUsageInstructions("Follow the laminated bench card next to the instrument.");
      e.setSafetyInfo("Wear PPE. Report faults to lab staff immediately.");
      e.setCondition(EquipmentCondition.GOOD);
      e.setCurrentStatus(EquipmentStatus.AVAILABLE);
      e.setMaintenanceStatus(MaintenanceStatus.OPERATIONAL);
      e.setCreatedBy(vendor);
      equipment.save(e);
      added++;
    }
    log.info("Demo seed finished: vendor '{}', {} new instruments", vendorUsername, added);
  }
}
