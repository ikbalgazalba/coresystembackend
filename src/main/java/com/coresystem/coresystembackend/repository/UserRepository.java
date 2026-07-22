package com.coresystem.coresystembackend.repository;
// SDD-PROVENANCE: U-003 | vault: .mega-sdd/vaults/jwt-login | JpaRepository<Users,Long> + derived findByUname (replaces newmojf users_Service.findUserAccount)

import java.util.Optional;

import com.coresystem.coresystembackend.entity.Users;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<Users, Long> {

	Optional<Users> findByUname(String uname);
}
