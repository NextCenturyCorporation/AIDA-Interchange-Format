import unittest
from io import BytesIO

from aida_interchange.aida_rdf_ontologies import AIDA_PROGRAM_ONTOLOGY
from aida_interchange.aifutils import make_system_with_uri, make_entity, \
  make_graph, mark_type, mark_text_justification


# Running these tests will output the examples to the console
class Examples(unittest.TestCase):
  def test_make_entity(self):
    g = make_graph()
    system = make_system_with_uri(g, "http://www.test.edu/system")
    entity = make_entity(g, "http://www.test.edu/entities/1", system)
    type_assertion = mark_type(g, "http://www.test.edu/assertions/1", entity,
                               AIDA_PROGRAM_ONTOLOGY.Person, system, 1.0)

    mark_text_justification(g, [entity, type_assertion], "NYT_ENG_20181231",
                            42, 143, system, 0.973)

    self.dump_graph(g, "Example of creating an entity")

  def dump_graph(self, g, description):
    print("\n\n======================================\n"
          "{!s}\n"
          "======================================\n\n".format(description))
    serialization = BytesIO()
    # need .buffer because serialize will write bytes, not str
    g.serialize(destination=serialization, format='turtle')
    print(serialization.getvalue().decode('utf-8'))
