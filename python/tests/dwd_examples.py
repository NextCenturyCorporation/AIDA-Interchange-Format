# Remove the below comment once we update to python3
# -*- coding: utf-8 -*-
import os
import sys
import unittest
from io import BytesIO

from aida_interchange import aifutils

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))

prefix = "http://www.test.edu/"
def get_initialized_graph_and_system():
    graph = aifutils.make_graph()
    graph.bind('test', prefix)
    system = aifutils.make_system_with_uri(graph, "http://www.test.edu/testSystem")
    return graph, system

# Running these tests will output the examples to the console
class DWDExamples(unittest.TestCase):
    test_dir_path = "./output"

    def test_create_event(self):
        g, system = get_initialized_graph_and_system()

        # make event with type election
        election = aifutils.make_event(g, prefix + "event/1", system)
        aifutils.mark_type(g, prefix + "types/1", election, "Q40231", system, 1.0)

        # make entity with type person
        putin = aifutils.make_entity(g, prefix + "entity/putin", system)
        aifutils.mark_type(g, prefix + "types/2", putin, "Q5", system, 1.0)

        # make entity with type political region
        russia = aifutils.make_entity(g, prefix + "enitty/russia", system)
        aifutils.mark_type(g, prefix + "types/3", russia, "Q1048835", system, 1.0)

        # add putin as cadidate for the election
        aifutils.mark_as_argument(g, election, "A1_ppt_theme_candidate",
                                  putin, system, .785, prefix + "arg/candidate")
        # add russia as location for election
        aifutils.mark_as_argument(g, election, "AM_loc__location",
                                  russia, system, .589, prefix + "arg/location")

        self.new_file(g, "test_create_event.ttl")
        self.dump_graph(g, "Example of event and entity")

    def new_file(self, g, test_name):
        if self.test_dir_path:
            f = open(self.test_dir_path + "/" + test_name, "wb+")
            f.write(g.serialize(format='turtle'))
            f.close()

    def dump_graph(self, g, description):
        print("\n\n======================================\n"
              "{!s}\n"
              "======================================\n\n".format(description))
        serialization = BytesIO()
        # need .buffer because serialize will write bytes, not str
        g.serialize(destination=serialization, format='turtle')
        print(serialization.getvalue().decode('utf-8'))

if __name__ == '__main__':
    # get directory path
    DWDExamples.test_dir_path = os.environ.get("DIR_PATH", None)
    if DWDExamples.test_dir_path is not None:
        if not os.path.exists(DWDExamples.test_dir_path):
            DWDExamples.test_dir_path = None
            print("Test output directory does not exist. Example turtle files will not be saved")
    else:
        print("Test output directory was not provided. Example turtle files will not be saved")

    unittest.main()
