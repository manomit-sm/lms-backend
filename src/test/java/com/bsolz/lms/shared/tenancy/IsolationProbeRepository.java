package com.bsolz.lms.shared.tenancy;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IsolationProbeRepository extends JpaRepository<IsolationProbe, UUID> {
}
