package io.github.posseidon.knowledgebase.it.interview.repo;

import io.github.posseidon.knowledgebase.it.interview.domain.skill.Skill;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SkillRepository extends JpaRepository<Skill, UUID> {

  List<Skill> findByParentIsNullOrderByName();

  List<Skill> findByParent_IdOrderByName(UUID parentId);

  Optional<Skill> findByPath(String path);

  @Query("SELECT s.parent.id, COUNT(s) FROM Skill s WHERE s.parent.id IN :parentIds GROUP BY s.parent.id")
  List<Object[]> countChildrenByParentIds(@Param("parentIds") Collection<UUID> parentIds);

  @Query("SELECT s FROM Skill s WHERE LOWER(s.name) IN :lowerNames")
  List<Skill> findByNameIgnoreCaseIn(@Param("lowerNames") Collection<String> lowerNames);

  @Query(value = """
      SELECT s.* FROM skill s
      WHERE s.name ILIKE '%' || :q || '%'
      ORDER BY (lower(s.name) = lower(:q)) DESC,
               lower(s.name) ASC
      LIMIT :limit
      """, nativeQuery = true)
  List<Skill> search(@Param("q") String q, @Param("limit") int limit);
}
